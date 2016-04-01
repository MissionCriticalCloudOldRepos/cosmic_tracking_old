//

//

package com.cloud.hypervisor.kvm.resource.wrapper;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.NetworkRulesVmSecondaryIpCommand;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;

import org.apache.log4j.Logger;
import org.libvirt.Connect;
import org.libvirt.LibvirtException;

@ResourceWrapper(handles = NetworkRulesVmSecondaryIpCommand.class)
public final class LibvirtNetworkRulesVmSecondaryIpCommandWrapper
    extends CommandWrapper<NetworkRulesVmSecondaryIpCommand, Answer, LibvirtComputingResource> {

  private static final Logger s_logger = Logger.getLogger(LibvirtNetworkRulesVmSecondaryIpCommandWrapper.class);

  @Override
  public Answer execute(final NetworkRulesVmSecondaryIpCommand command,
      final LibvirtComputingResource libvirtComputingResource) {
    boolean result = false;
    try {
      final LibvirtUtilitiesHelper libvirtUtilitiesHelper = libvirtComputingResource.getLibvirtUtilitiesHelper();

      final Connect conn = libvirtUtilitiesHelper.getConnectionByVmName(command.getVmName());
      result = libvirtComputingResource.configureNetworkRulesVmSecondaryIp(conn, command.getVmName(),
          command.getVmSecIp(), command.getAction());
    } catch (final LibvirtException e) {
      s_logger.debug("Could not configure VM secondary IP! => " + e.getLocalizedMessage());
    }

    return new Answer(command, result, "");
  }
}