package com.cloud.ha;

import java.util.List;

import javax.inject.Inject;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.CheckOnHostCommand;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.resource.ResourceManager;
import com.cloud.utils.component.AdapterBase;

import org.apache.log4j.Logger;

public class KVMInvestigator extends AdapterBase implements Investigator {
  private final static Logger s_logger = Logger.getLogger(KVMInvestigator.class);
  @Inject
  HostDao _hostDao;
  @Inject
  AgentManager _agentMgr;
  @Inject
  ResourceManager _resourceMgr;

  @Override
  public boolean isVmAlive(com.cloud.vm.VirtualMachine vm, Host host) throws UnknownVM {
    final Status status = isAgentAlive(host);
    if (status == null) {
      throw new UnknownVM();
    }
    if (status == Status.Up) {
      return true;
    } else {
      throw new UnknownVM();
    }
  }

  @Override
  public Status isAgentAlive(Host agent) {
    if (agent.getHypervisorType() != Hypervisor.HypervisorType.KVM && agent.getHypervisorType() != Hypervisor.HypervisorType.LXC) {
      return null;
    }
    Status hostStatus = null;
    Status neighbourStatus = null;
    final CheckOnHostCommand cmd = new CheckOnHostCommand(agent);

    try {
      final Answer answer = _agentMgr.easySend(agent.getId(), cmd);
      if (answer != null) {
        hostStatus = answer.getResult() ? Status.Down : Status.Up;
      }
    } catch (final Exception e) {
      s_logger.debug("Failed to send command to host: " + agent.getId());
    }
    if (hostStatus == null) {
      hostStatus = Status.Disconnected;
    }

    final List<HostVO> neighbors = _resourceMgr.listHostsInClusterByStatus(agent.getClusterId(), Status.Up);
    for (final HostVO neighbor : neighbors) {
      if (neighbor.getId() == agent.getId() || neighbor.getHypervisorType() != Hypervisor.HypervisorType.KVM && neighbor.getHypervisorType() != Hypervisor.HypervisorType.LXC) {
        continue;
      }
      s_logger.debug("Investigating host:" + agent.getId() + " via neighbouring host:" + neighbor.getId());
      try {
        final Answer answer = _agentMgr.easySend(neighbor.getId(), cmd);
        if (answer != null) {
          neighbourStatus = answer.getResult() ? Status.Down : Status.Up;
          s_logger.debug("Neighbouring host:" + neighbor.getId() + " returned status:" + neighbourStatus + " for the investigated host:" + agent.getId());
          if (neighbourStatus == Status.Up) {
            break;
          }
        }
      } catch (final Exception e) {
        s_logger.debug("Failed to send command to host: " + neighbor.getId());
      }
    }
    if (neighbourStatus == Status.Up && (hostStatus == Status.Disconnected || hostStatus == Status.Down)) {
      hostStatus = Status.Disconnected;
    }
    if (neighbourStatus == Status.Down && (hostStatus == Status.Disconnected || hostStatus == Status.Down)) {
      hostStatus = Status.Down;
    }
    return hostStatus;
  }
}