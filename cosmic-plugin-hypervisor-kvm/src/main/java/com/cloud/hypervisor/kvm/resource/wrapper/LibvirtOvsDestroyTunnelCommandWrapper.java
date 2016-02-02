//

//

package com.cloud.hypervisor.kvm.resource.wrapper;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.OvsDestroyTunnelCommand;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import com.cloud.utils.script.Script;

import org.apache.log4j.Logger;

@ResourceWrapper(handles = OvsDestroyTunnelCommand.class)
public final class LibvirtOvsDestroyTunnelCommandWrapper
    extends CommandWrapper<OvsDestroyTunnelCommand, Answer, LibvirtComputingResource> {

  private static final Logger s_logger = Logger.getLogger(LibvirtOvsDestroyTunnelCommandWrapper.class);

  @Override
  public Answer execute(final OvsDestroyTunnelCommand command,
      final LibvirtComputingResource libvirtComputingResource) {
    try {
      if (!libvirtComputingResource.findOrCreateTunnelNetwork(command.getBridgeName())) {
        s_logger.warn("Unable to find tunnel network for GRE key:"
            + command.getBridgeName());
        return new Answer(command, false, "No network found");
      }

      final Script scriptCommand = new Script(libvirtComputingResource.getOvsTunnelPath(),
          libvirtComputingResource.getTimeout(), s_logger);
      scriptCommand.add("destroy_tunnel");
      scriptCommand.add("--bridge", command.getBridgeName());
      scriptCommand.add("--iface_name", command.getInPortName());
      final String result = scriptCommand.execute();
      if (result == null) {
        return new Answer(command, true, result);
      } else {
        return new Answer(command, false, result);
      }
    } catch (final Exception e) {
      s_logger.warn("caught execption when destroy ovs tunnel", e);
      return new Answer(command, false, e.getMessage());
    }
  }
}