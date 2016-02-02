//

//

package com.cloud.hypervisor.kvm.resource.wrapper;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.OvsDestroyBridgeCommand;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;

import org.apache.log4j.Logger;

@ResourceWrapper(handles = OvsDestroyBridgeCommand.class)
public final class LibvirtOvsDestroyBridgeCommandWrapper
    extends CommandWrapper<OvsDestroyBridgeCommand, Answer, LibvirtComputingResource> {

  private static final Logger s_logger = Logger.getLogger(LibvirtOvsDestroyBridgeCommandWrapper.class);

  @Override
  public Answer execute(final OvsDestroyBridgeCommand command,
      final LibvirtComputingResource libvirtComputingResource) {
    final boolean result = libvirtComputingResource.destroyTunnelNetwork(command.getBridgeName());

    if (!result) {
      s_logger.debug("Error trying to destroy OVS Bridge!");
    }

    return new Answer(command, result, null);
  }
}