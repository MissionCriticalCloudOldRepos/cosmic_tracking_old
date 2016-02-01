package com.cloud.hypervisor.kvm.resource;

import java.io.File;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.ConfigurationException;

import com.cloud.agent.api.to.NicTO;
import com.cloud.exception.InternalErrorException;
import com.cloud.hypervisor.kvm.resource.LibvirtVmDef.InterfaceDef;
import com.cloud.network.Networks;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.net.NetUtils;
import com.cloud.utils.script.OutputInterpreter;
import com.cloud.utils.script.Script;

import org.apache.log4j.Logger;
import org.libvirt.LibvirtException;

public class IvsVifDriver extends VifDriverBase {

  private final Logger logger = Logger.getLogger(IvsVifDriver.class);

  private int timeout;

  private final Object vnetBridgeMonitor = new Object();
  private String modifyVlanPath;
  private String modifyVxlanPath;
  private String ivsIfUpPath;
  private Long libvirtVersion;

  @Override
  public void configure(Map<String, Object> params) throws ConfigurationException {
    super.configure(params);
    String networkScriptsDir = (String) params.get("network.scripts.dir");
    if (networkScriptsDir == null) {
      networkScriptsDir = "scripts/vm/network/vnet";
    }
    final String utilScriptsDir = "scripts/util/";

    final String value = (String) params.get("scripts.timeout");
    timeout = NumbersUtil.parseInt(value, 30 * 60) * 1000;

    modifyVlanPath = Script.findScript(networkScriptsDir, "modifyvlan.sh");
    if (modifyVlanPath == null) {
      throw new ConfigurationException("Unable to find modifyvlan.sh");
    }
    modifyVxlanPath = Script.findScript(networkScriptsDir, "modifyvxlan.sh");
    if (modifyVxlanPath == null) {
      throw new ConfigurationException("Unable to find modifyvxlan.sh");
    }
    ivsIfUpPath = Script.findScript(utilScriptsDir, "qemu-ivs-ifup");

    libvirtVersion = (Long) params.get("libvirtVersion");
    if (libvirtVersion == null) {
      libvirtVersion = 0L;
    }

    createControlNetwork(bridges.get("linklocal"));
  }

  @Override
  public InterfaceDef plug(NicTO nic, String guestOsType, String nicAdapter)
      throws InternalErrorException, LibvirtException {
    final LibvirtVmDef.InterfaceDef intf = new LibvirtVmDef.InterfaceDef();

    String netId = null;
    String protocol = null;
    if (nic.getBroadcastType() == Networks.BroadcastDomainType.Vlan
        || nic.getBroadcastType() == Networks.BroadcastDomainType.Vxlan) {
      netId = Networks.BroadcastDomainType.getValue(nic.getBroadcastUri());
      protocol = Networks.BroadcastDomainType.getSchemeValue(nic.getBroadcastUri()).scheme();
    }

    String vlanId = null;
    if (nic.getBroadcastType() == Networks.BroadcastDomainType.Vlan) {
      vlanId = Networks.BroadcastDomainType.getValue(nic.getBroadcastUri());
    } else if (nic.getBroadcastType() == Networks.BroadcastDomainType.Lswitch) {
      Networks.BroadcastDomainType.getValue(nic.getBroadcastUri());
    } else if (nic.getBroadcastType() == Networks.BroadcastDomainType.Pvlan) {
      // TODO consider moving some of this functionality from NetUtils to Networks....
      vlanId = NetUtils.getPrimaryPvlanFromUri(nic.getBroadcastUri());
    }
    final String trafficLabel = nic.getName();
    final Integer networkRateKBps = nic.getNetworkRateMbps() != null && nic.getNetworkRateMbps().intValue() != -1
        ? nic.getNetworkRateMbps().intValue() * 128 : 0;
    if (nic.getType() == Networks.TrafficType.Guest) {
      if ((nic.getBroadcastType() == Networks.BroadcastDomainType.Vlan
          || nic.getBroadcastType() == Networks.BroadcastDomainType.Pvlan)
          && !vlanId.equalsIgnoreCase("untagged")) {
        if (trafficLabel != null && !trafficLabel.isEmpty()) {
          logger.debug("creating a vlan dev and bridge for guest traffic per traffic label " + trafficLabel);
          intf.defEthernet("ivsnet-" + nic.getUuid().substring(0, 5), nic.getMac(),
              getGuestNicModel(guestOsType, nicAdapter), ivsIfUpPath, networkRateKBps);
        } else {
          throw new InternalErrorException("no traffic label ");
        }
      }
    } else if (nic.getType() == Networks.TrafficType.Control) {
      /* Make sure the network is still there */
      createControlNetwork();
      intf.defBridgeNet(bridges.get("linklocal"), null, nic.getMac(), getGuestNicModel(guestOsType, nicAdapter));
    } else if (nic.getType() == Networks.TrafficType.Public) {
      if (nic.getBroadcastType() == Networks.BroadcastDomainType.Vlan && netId != null && protocol != null
          && !netId.equalsIgnoreCase("untagged")
          || nic.getBroadcastType() == Networks.BroadcastDomainType.Vxlan) {
        if (trafficLabel != null && !trafficLabel.isEmpty()) {
          logger.debug("creating a vNet dev and bridge for public traffic per traffic label " + trafficLabel);
          final String brName = createVnetBr(netId, trafficLabel, protocol);
          intf.defBridgeNet(brName, null, nic.getMac(), getGuestNicModel(guestOsType, nicAdapter), networkRateKBps);
        } else {
          final String brName = createVnetBr(netId, "public", protocol);
          intf.defBridgeNet(brName, null, nic.getMac(), getGuestNicModel(guestOsType, nicAdapter), networkRateKBps);
        }
      } else {
        intf.defBridgeNet(bridges.get("public"), null, nic.getMac(), getGuestNicModel(guestOsType, nicAdapter),
            networkRateKBps);
      }
    } else if (nic.getType() == Networks.TrafficType.Management) {
      intf.defBridgeNet(bridges.get("private"), null, nic.getMac(), getGuestNicModel(guestOsType, nicAdapter));
    } else if (nic.getType() == Networks.TrafficType.Storage) {
      final String storageBrName = nic.getName() == null ? bridges.get("private") : nic.getName();
      intf.defBridgeNet(storageBrName, null, nic.getMac(), getGuestNicModel(guestOsType, nicAdapter));
    }
    if (nic.getPxeDisable() == true) {
      intf.setPxeDisable(true);
    }
    return intf;
  }

  @Override
  public void unplug(InterfaceDef iface) {
  }

  private void createControlNetwork() throws LibvirtException {
    createControlNetwork(bridges.get("linklocal"));
  }

  private void createControlNetwork(String privBrName) {
    deleteExitingLinkLocalRouteTable(privBrName);
    if (!isBridgeExists(privBrName)) {
      Script.runSimpleBashScript("brctl addbr " + privBrName + "; ip link set " + privBrName
          + " up; ip address add 169.254.0.1/16 dev " + privBrName, timeout);
    }
  }

  private String createVnetBr(String netId, String pifKey, String protocol) throws InternalErrorException {
    String nic = pifs.get(pifKey);
    if (nic == null) {
      // if not found in bridge map, maybe traffic label refers to pif already?
      final File pif = new File("/sys/class/net/" + pifKey);
      if (pif.isDirectory()) {
        nic = pifKey;
      }
    }
    String brName = "";
    brName = setVnetBrName(nic, netId);
    createVnet(netId, nic, brName, protocol);
    return brName;
  }

  private String setVnetBrName(String pifName, String vnetId) {
    return "br" + pifName + "-" + vnetId;
  }

  private void createVnet(String vnetId, String pif, String brName, String protocol) throws InternalErrorException {
    synchronized (vnetBridgeMonitor) {
      String script = modifyVlanPath;
      if (protocol.equals(Networks.BroadcastDomainType.Vxlan.scheme())) {
        script = modifyVxlanPath;
      }
      final Script command = new Script(script, timeout, logger);
      command.add("-v", vnetId);
      command.add("-p", pif);
      command.add("-b", brName);
      command.add("-o", "add");

      final String result = command.execute();
      if (result != null) {
        throw new InternalErrorException("Failed to create vnet " + vnetId + ": " + result);
      }
    }
  }

  private void deleteVnetBr(String brName) {
    synchronized (vnetBridgeMonitor) {
      String cmdout = Script.runSimpleBashScript("ls /sys/class/net/" + brName);
      if (cmdout == null) {
        // Bridge does not exist
        return;
      }
      cmdout = Script.runSimpleBashScript("ls /sys/class/net/" + brName + "/brif | tr '\n' ' '");
      if (cmdout != null && cmdout.contains("vnet")) {
        // Active VM remains on that bridge
        return;
      }

      final Pattern oldStyleBrNameRegex = Pattern.compile("^cloudVirBr(\\d+)$");
      final Pattern brNameRegex = Pattern.compile("^br(\\S+)-(\\d+)$");
      final Matcher oldStyleBrNameMatcher = oldStyleBrNameRegex.matcher(brName);
      final Matcher brNameMatcher = brNameRegex.matcher(brName);

      String name = null;
      String netId = null;
      if (oldStyleBrNameMatcher.find()) {
        // Actually modifyvlan.sh doesn't require pif name when deleting its bridge so far.
        name = "undefined";
        netId = oldStyleBrNameMatcher.group(1);
      } else if (brNameMatcher.find()) {
        if (brNameMatcher.group(1) != null || !brNameMatcher.group(1).isEmpty()) {
          name = brNameMatcher.group(1);
        } else {
          name = "undefined";
        }
        netId = brNameMatcher.group(2);
      }

      if (netId == null || netId.isEmpty()) {
        logger.debug("unable to get a vNet ID from name " + brName);
        return;
      }

      String scriptPath = null;
      if (cmdout != null && cmdout.contains("vxlan")) {
        scriptPath = modifyVxlanPath;
      } else {
        scriptPath = modifyVlanPath;
      }

      final Script command = new Script(scriptPath, timeout, logger);
      command.add("-o", "delete");
      command.add("-v", netId);
      command.add("-p", name);
      command.add("-b", brName);

      final String result = command.execute();
      if (result != null) {
        logger.debug("Delete bridge " + brName + " failed: " + result);
      }
    }
  }

  private void deleteExitingLinkLocalRouteTable(String linkLocalBr) {
    final Script command = new Script("/bin/bash", timeout);
    command.add("-c");
    command.add("ip route | grep " + NetUtils.getLinkLocalCIDR());
    final OutputInterpreter.AllLinesParser parser = new OutputInterpreter.AllLinesParser();
    final String result = command.execute(parser);
    boolean foundLinkLocalBr = false;
    if (result == null && parser.getLines() != null) {
      final String[] lines = parser.getLines().split("\\n");
      for (final String line : lines) {
        final String[] tokens = line.split(" ");
        if (!tokens[2].equalsIgnoreCase(linkLocalBr)) {
          Script.runSimpleBashScript("ip route del " + NetUtils.getLinkLocalCIDR());
        } else {
          foundLinkLocalBr = true;
        }
      }
    }
    if (!foundLinkLocalBr) {
      Script.runSimpleBashScript("ip address add 169.254.0.1/16 dev " + linkLocalBr + ";" + "ip route add "
          + NetUtils.getLinkLocalCIDR() + " dev " + linkLocalBr + " src "
          + NetUtils.getLinkLocalGateway());
    }
  }

  private boolean isBridgeExists(String bridgeName) {
    final File f = new File("/sys/devices/virtual/net/" + bridgeName);
    if (f.exists()) {
      return true;
    } else {
      return false;
    }
  }
}