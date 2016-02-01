//

//

package com.cloud.hypervisor.kvm.resource.wrapper;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.NetworkRulesSystemVmCommand;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;

import org.apache.log4j.Logger;
import org.libvirt.Connect;
import org.libvirt.LibvirtException;

@ResourceWrapper(handles = NetworkRulesSystemVmCommand.class)
public final class LibvirtNetworkRulesSystemVmCommandWrapper
    extends CommandWrapper<NetworkRulesSystemVmCommand, Answer, LibvirtComputingResource> {

  private static final Logger s_logger = Logger.getLogger(LibvirtOvsVpcRoutingPolicyConfigCommandWrapper.class);

  @Override
  public Answer execute(final NetworkRulesSystemVmCommand command,
      final LibvirtComputingResource libvirtComputingResource) {
    boolean success = false;
    try {
      final LibvirtUtilitiesHelper libvirtUtilitiesHelper = libvirtComputingResource.getLibvirtUtilitiesHelper();

      final Connect conn = libvirtUtilitiesHelper.getConnectionByVmName(command.getVmName());
      success = libvirtComputingResource.configureDefaultNetworkRulesForSystemVm(conn, command.getVmName());
    } catch (final LibvirtException e) {
      s_logger.trace("Ignoring libvirt error.", e);
    }

    return new Answer(command, success, "");
  }
}