//

//

package com.cloud.hypervisor.kvm.resource.wrapper;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.OvsFetchInterfaceAnswer;
import com.cloud.agent.api.OvsFetchInterfaceCommand;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import com.cloud.utils.script.Script;

import org.apache.log4j.Logger;

@ResourceWrapper(handles = OvsFetchInterfaceCommand.class)
public final class LibvirtOvsFetchInterfaceCommandWrapper
    extends CommandWrapper<OvsFetchInterfaceCommand, Answer, LibvirtComputingResource> {

  private static final Logger s_logger = Logger.getLogger(LibvirtOvsFetchInterfaceCommandWrapper.class);

  @Override
  public Answer execute(final OvsFetchInterfaceCommand command,
      final LibvirtComputingResource libvirtComputingResource) {
    final String label = command.getLabel();

    s_logger.debug("Will look for network with name-label:" + label);
    try {
      final String ipadd = Script.runSimpleBashScript(
          "ifconfig " + label + " | grep 'inet addr:' | cut -d: -f2 | awk '{ print $1}'");
      final String mask = Script.runSimpleBashScript("ifconfig " + label + " | grep 'inet addr:' | cut -d: -f4");
      final String mac = Script.runSimpleBashScript("ifconfig " + label + " | grep HWaddr | awk -F \" \" '{print $5}'");
      return new OvsFetchInterfaceAnswer(command, true, "Interface " + label
          + " retrieved successfully", ipadd, mask, mac);

    } catch (final Exception e) {
      s_logger.warn("Caught execption when fetching interface", e);
      return new OvsFetchInterfaceAnswer(command, false, "EXCEPTION:"
          + e.getMessage());
    }
  }
}