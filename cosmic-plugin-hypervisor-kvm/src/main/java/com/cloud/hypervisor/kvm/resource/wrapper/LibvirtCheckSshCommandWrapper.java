package com.cloud.hypervisor.kvm.resource.wrapper;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.check.CheckSshAnswer;
import com.cloud.agent.api.check.CheckSshCommand;
import com.cloud.agent.resource.virtualnetwork.VirtualRoutingResource;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;

import org.apache.log4j.Logger;

@ResourceWrapper(handles = CheckSshCommand.class)
public final class LibvirtCheckSshCommandWrapper
    extends CommandWrapper<CheckSshCommand, Answer, LibvirtComputingResource> {

  private static final Logger s_logger = Logger.getLogger(LibvirtOvsVpcRoutingPolicyConfigCommandWrapper.class);

  @Override
  public Answer execute(final CheckSshCommand command, final LibvirtComputingResource libvirtComputingResource) {
    final String vmName = command.getName();
    final String privateIp = command.getIp();
    final int cmdPort = command.getPort();

    if (s_logger.isDebugEnabled()) {
      s_logger.debug("Ping command port, " + privateIp + ":" + cmdPort);
    }

    final VirtualRoutingResource virtRouterResource = libvirtComputingResource.getVirtRouterResource();
    if (!virtRouterResource.connect(privateIp, cmdPort)) {
      return new CheckSshAnswer(command, "Can not ping System vm " + vmName + " because of a connection failure");
    }

    if (s_logger.isDebugEnabled()) {
      s_logger.debug("Ping command port succeeded for vm " + vmName);
    }

    return new CheckSshAnswer(command);
  }
}