package com.cloud.hypervisor.kvm.resource;

import com.cloud.agent.api.to.NicTO;
import com.cloud.exception.InternalErrorException;
import com.cloud.network.Networks;

import org.libvirt.LibvirtException;

public class DirectVifDriver extends VifDriverBase {

  @Override
  public LibvirtVmDef.InterfaceDef plug(NicTO nic, String guestOsType, String nicAdapter)
      throws InternalErrorException, LibvirtException {
    final LibvirtVmDef.InterfaceDef intf = new LibvirtVmDef.InterfaceDef();

    if (nic.getType() == Networks.TrafficType.Guest) {
      final Integer networkRateKBps = nic.getNetworkRateMbps() != null && nic.getNetworkRateMbps().intValue() != -1
          ? nic.getNetworkRateMbps().intValue() * 128 : 0;

      intf.defDirectNet(libvirtComputingResource.getNetworkDirectDevice(), null, nic.getMac(),
          getGuestNicModel(guestOsType, nicAdapter),
          libvirtComputingResource.getNetworkDirectSourceMode(), networkRateKBps);

    } else if (nic.getType() == Networks.TrafficType.Public) {
      final Integer networkRateKBps = nic.getNetworkRateMbps() != null && nic.getNetworkRateMbps().intValue() != -1
          ? nic.getNetworkRateMbps().intValue() * 128 : 0;

      intf.defDirectNet(libvirtComputingResource.getNetworkDirectDevice(), null, nic.getMac(),
          getGuestNicModel(guestOsType, nicAdapter),
          libvirtComputingResource.getNetworkDirectSourceMode(), networkRateKBps);
    }

    return intf;
  }

  @Override
  public void unplug(LibvirtVmDef.InterfaceDef iface) {
  }
}