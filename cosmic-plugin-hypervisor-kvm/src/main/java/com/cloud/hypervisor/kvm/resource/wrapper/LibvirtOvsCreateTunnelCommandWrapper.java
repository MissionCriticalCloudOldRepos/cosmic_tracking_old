//

//

package com.cloud.hypervisor.kvm.resource.wrapper;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.OvsCreateTunnelAnswer;
import com.cloud.agent.api.OvsCreateTunnelCommand;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import com.cloud.utils.script.Script;

import org.apache.log4j.Logger;

@ResourceWrapper(handles = OvsCreateTunnelCommand.class)
public final class LibvirtOvsCreateTunnelCommandWrapper
    extends CommandWrapper<OvsCreateTunnelCommand, Answer, LibvirtComputingResource> {

  private static final Logger s_logger = Logger.getLogger(LibvirtOvsCreateTunnelCommandWrapper.class);

  @Override
  public Answer execute(final OvsCreateTunnelCommand command, final LibvirtComputingResource libvirtComputingResource) {
    final String bridge = command.getNetworkName();
    try {
      if (!libvirtComputingResource.findOrCreateTunnelNetwork(bridge)) {
        s_logger.debug("Error during bridge setup");
        return new OvsCreateTunnelAnswer(command, false,
            "Cannot create network", bridge);
      }

      libvirtComputingResource.configureTunnelNetwork(command.getNetworkId(), command.getFrom(),
          command.getNetworkName());

      final Script scriptCommand = new Script(libvirtComputingResource.getOvsTunnelPath(),
          libvirtComputingResource.getTimeout(), s_logger);
      scriptCommand.add("create_tunnel");
      scriptCommand.add("--bridge", bridge);
      scriptCommand.add("--remote_ip", command.getRemoteIp());
      scriptCommand.add("--key", command.getKey().toString());
      scriptCommand.add("--src_host", command.getFrom().toString());
      scriptCommand.add("--dst_host", command.getTo().toString());

      final String result = scriptCommand.execute();
      if (result != null) {
        return new OvsCreateTunnelAnswer(command, true, result, null,
            bridge);
      } else {
        return new OvsCreateTunnelAnswer(command, false, result, bridge);
      }
    } catch (final Exception e) {
      s_logger.warn("Caught execption when creating ovs tunnel", e);
      return new OvsCreateTunnelAnswer(command, false, e.getMessage(), bridge);
    }
  }
}