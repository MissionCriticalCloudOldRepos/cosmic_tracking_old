//

//

package com.cloud.hypervisor.kvm.resource.wrapper;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.OvsSetupBridgeCommand;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;

import org.apache.log4j.Logger;

@ResourceWrapper(handles = OvsSetupBridgeCommand.class)
public final class LibvirtOvsSetupBridgeCommandWrapper
    extends CommandWrapper<OvsSetupBridgeCommand, Answer, LibvirtComputingResource> {

  private static final Logger s_logger = Logger.getLogger(LibvirtOvsSetupBridgeCommandWrapper.class);

  @Override
  public Answer execute(final OvsSetupBridgeCommand command, final LibvirtComputingResource libvirtComputingResource) {
    final boolean findResult = libvirtComputingResource.findOrCreateTunnelNetwork(command.getBridgeName());
    final boolean configResult = libvirtComputingResource.configureTunnelNetwork(command.getNetworkId(),
        command.getHostId(),
        command.getBridgeName());

    final boolean finalResult = findResult && configResult;

    if (!finalResult) {
      s_logger.debug("::FAILURE:: OVS Bridge was NOT configured properly!");
    }

    return new Answer(command, finalResult, null);
  }
}