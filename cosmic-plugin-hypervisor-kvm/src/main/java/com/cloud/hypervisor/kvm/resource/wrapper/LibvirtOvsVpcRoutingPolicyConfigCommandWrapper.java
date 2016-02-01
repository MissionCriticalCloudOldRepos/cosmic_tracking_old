//

//

package com.cloud.hypervisor.kvm.resource.wrapper;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.OvsVpcRoutingPolicyConfigCommand;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import com.cloud.utils.script.Script;

import org.apache.log4j.Logger;

@ResourceWrapper(handles = OvsVpcRoutingPolicyConfigCommand.class)
public final class LibvirtOvsVpcRoutingPolicyConfigCommandWrapper
    extends CommandWrapper<OvsVpcRoutingPolicyConfigCommand, Answer, LibvirtComputingResource> {

  private static final Logger s_logger = Logger.getLogger(LibvirtOvsVpcRoutingPolicyConfigCommandWrapper.class);

  @Override
  public Answer execute(final OvsVpcRoutingPolicyConfigCommand command,
      final LibvirtComputingResource libvirtComputingResource) {
    try {
      final Script scriptCommand = new Script(libvirtComputingResource.getOvsTunnelPath(),
          libvirtComputingResource.getTimeout(), s_logger);
      scriptCommand.add("configure_ovs_bridge_for_routing_policies");
      scriptCommand.add("--bridge", command.getBridgeName());
      scriptCommand.add("--config", command.getVpcConfigInJson());

      final String result = scriptCommand.execute();
      if (result.equalsIgnoreCase("SUCCESS")) {
        return new Answer(command, true, result);
      } else {
        return new Answer(command, false, result);
      }
    } catch (final Exception e) {
      s_logger.warn("caught exception while updating host with latest VPC topology", e);
      return new Answer(command, false, e.getMessage());
    }
  }
}