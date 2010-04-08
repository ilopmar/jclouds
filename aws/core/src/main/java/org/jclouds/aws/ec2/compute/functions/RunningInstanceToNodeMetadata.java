package org.jclouds.aws.ec2.compute.functions;

import java.net.InetAddress;
import java.net.URI;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.jclouds.aws.ec2.compute.domain.KeyPairCredentials;
import org.jclouds.aws.ec2.compute.domain.RegionTag;
import org.jclouds.aws.ec2.domain.Image;
import org.jclouds.aws.ec2.domain.InstanceState;
import org.jclouds.aws.ec2.domain.RunningInstance;
import org.jclouds.aws.ec2.options.DescribeImagesOptions;
import org.jclouds.aws.ec2.services.AMIClient;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeState;
import org.jclouds.compute.domain.internal.NodeMetadataImpl;
import org.jclouds.domain.Credentials;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

@Singleton
public class RunningInstanceToNodeMetadata implements Function<RunningInstance, NodeMetadata> {
   private static final Map<InstanceState, NodeState> instanceToNodeState = ImmutableMap
            .<InstanceState, NodeState> builder().put(InstanceState.PENDING, NodeState.PENDING)
            .put(InstanceState.RUNNING, NodeState.RUNNING).put(InstanceState.SHUTTING_DOWN,
                     NodeState.PENDING).put(InstanceState.TERMINATED, NodeState.TERMINATED).build();

   private final AMIClient amiClient;
   private final Map<RegionTag, KeyPairCredentials> credentialsMap;
   private final ImageParser imageParser;

   @Inject
   public RunningInstanceToNodeMetadata(AMIClient amiClient,
            Map<RegionTag, KeyPairCredentials> credentialsMap,
            ImageParser imageParser) {
      this.amiClient = amiClient;
      this.credentialsMap = credentialsMap;
      this.imageParser = imageParser;
   }

   @Override
   public NodeMetadata apply(RunningInstance from) {
      String id = from.getId();
      String name = null; // user doesn't determine a node name;
      URI uri = null; // no uri to get rest access to host info
      Map<String, String> userMetadata = ImmutableMap.<String, String> of();
      String tag = from.getKeyName();
      NodeState state = instanceToNodeState.get(from.getInstanceState());
      Set<InetAddress> publicAddresses = nullSafeSet(from.getIpAddress());
      Set<InetAddress> privateAddresses = nullSafeSet(from.getPrivateIpAddress());
      Credentials credentials = credentialsMap.containsKey(new RegionTag(from.getRegion(), tag)) ? credentialsMap
               .get(new RegionTag(from.getRegion(), tag))
               : null;
      Image image = Iterables.getOnlyElement(amiClient.describeImagesInRegion(from.getRegion(),
               DescribeImagesOptions.Builder.imageIds(from.getImageId())));

      // canonical/alestic images use the ubuntu user to login
      // TODO: add this as a property of image
      if (credentials != null && image.getImageOwnerId().matches("063491364108|099720109477"))
         credentials = new Credentials("ubuntu", credentials.key);

      if(credentials == null) credentials = imageParser.apply(image).getDefaultCredentials();

      String locationId = from.getAvailabilityZone().toString();
      Map<String, String> extra = ImmutableMap.<String, String> of();
      return new NodeMetadataImpl(id, name, locationId, uri, userMetadata, tag, state,
               publicAddresses, privateAddresses, extra, credentials);
   }

   Set<InetAddress> nullSafeSet(InetAddress in) {
      if (in == null) {
         return ImmutableSet.<InetAddress> of();
      }
      return ImmutableSet.<InetAddress> of(in);
   }

}