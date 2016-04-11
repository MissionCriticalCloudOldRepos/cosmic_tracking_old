/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cloudstack.storage.datastore.lifecycle;

import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.engine.subsystem.api.storage.ClusterScope;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.HostScope;
import org.apache.cloudstack.engine.subsystem.api.storage.PrimaryDataStoreInfo;
import org.apache.cloudstack.engine.subsystem.api.storage.PrimaryDataStoreLifeCycle;
import org.apache.cloudstack.engine.subsystem.api.storage.PrimaryDataStoreParameters;
import org.apache.cloudstack.engine.subsystem.api.storage.ZoneScope;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolDetailVO;
import org.apache.cloudstack.storage.datastore.db.StoragePoolDetailsDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;
import org.apache.cloudstack.storage.datastore.util.SolidFireUtil;
import org.apache.cloudstack.storage.volume.datastore.PrimaryDataStoreHelper;
import org.apache.log4j.Logger;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.CreateStoragePoolCommand;
import com.cloud.agent.api.DeleteStoragePoolCommand;
import com.cloud.agent.api.StoragePoolInfo;
import com.cloud.dc.ClusterDetailsDao;
import com.cloud.dc.ClusterDetailsVO;
import com.cloud.dc.ClusterVO;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.resource.ResourceManager;
import com.cloud.storage.Storage.StoragePoolType;
import com.cloud.storage.StorageManager;
import com.cloud.storage.StoragePool;
import com.cloud.storage.StoragePoolAutomation;
import com.cloud.storage.StoragePoolHostVO;
import com.cloud.storage.VMTemplateStoragePoolVO;
import com.cloud.storage.dao.StoragePoolHostDao;
import com.cloud.template.TemplateManager;
import com.cloud.user.Account;
import com.cloud.user.AccountDetailsDao;
import com.cloud.user.AccountVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.exception.CloudRuntimeException;

public class SolidFireSharedPrimaryDataStoreLifeCycle implements PrimaryDataStoreLifeCycle {
  private static final Logger s_logger = Logger.getLogger(SolidFireSharedPrimaryDataStoreLifeCycle.class);

  @Inject private AccountDao _accountDao;
  @Inject private AccountDetailsDao _accountDetailsDao;
  @Inject private AgentManager _agentMgr;
  @Inject private ClusterDao _clusterDao;
  @Inject private ClusterDetailsDao _clusterDetailsDao;
  @Inject private DataCenterDao _zoneDao;
  @Inject private HostDao _hostDao;
  @Inject private PrimaryDataStoreDao _primaryDataStoreDao;
  @Inject private PrimaryDataStoreHelper _primaryDataStoreHelper;
  @Inject private ResourceManager _resourceMgr;
  @Inject private StorageManager _storageMgr;
  @Inject private StoragePoolAutomation _storagePoolAutomation;
  @Inject private StoragePoolDetailsDao _storagePoolDetailsDao;
  @Inject private StoragePoolHostDao _storagePoolHostDao;
  @Inject protected TemplateManager _tmpltMgr;

  // invoked to add primary storage that is based on the SolidFire plug-in
  @Override
  public DataStore initialize(Map<String, Object> dsInfos) {
    final String CAPACITY_IOPS = "capacityIops";

    final String url = (String)dsInfos.get("url");
    final Long zoneId = (Long)dsInfos.get("zoneId");
    final Long podId = (Long)dsInfos.get("podId");
    final Long clusterId = (Long)dsInfos.get("clusterId");
    final String storagePoolName = (String)dsInfos.get("name");
    final String providerName = (String)dsInfos.get("providerName");
    final Long capacityBytes = (Long)dsInfos.get("capacityBytes");
    final Long capacityIops = (Long)dsInfos.get(CAPACITY_IOPS);
    final String tags = (String)dsInfos.get("tags");
    @SuppressWarnings("unchecked")
    final
    Map<String, String> details = (Map<String, String>)dsInfos.get("details");

    if (podId == null) {
      throw new CloudRuntimeException("The Pod ID must be specified.");
    }

    if (clusterId == null) {
      throw new CloudRuntimeException("The Cluster ID must be specified.");
    }

    final String storageVip = SolidFireUtil.getStorageVip(url);
    final int storagePort = SolidFireUtil.getStoragePort(url);

    if (capacityBytes == null || capacityBytes <= 0) {
      throw new IllegalArgumentException("'capacityBytes' must be present and greater than 0.");
    }

    if (capacityIops == null || capacityIops <= 0) {
      throw new IllegalArgumentException("'capacityIops' must be present and greater than 0.");
    }

    final HypervisorType hypervisorType = getHypervisorTypeForCluster(clusterId);

    if (!isSupportedHypervisorType(hypervisorType)) {
      throw new CloudRuntimeException(hypervisorType + " is not a supported hypervisor type.");
    }

    SolidFireUtil.getValue(SolidFireUtil.DATACENTER, url, false);

    final PrimaryDataStoreParameters parameters = new PrimaryDataStoreParameters();

    parameters.setType(getStorageType(hypervisorType));
    parameters.setZoneId(zoneId);
    parameters.setPodId(podId);
    parameters.setClusterId(clusterId);
    parameters.setName(storagePoolName);
    parameters.setProviderName(providerName);
    parameters.setManaged(false);
    parameters.setCapacityBytes(capacityBytes);
    parameters.setUsedBytes(0);
    parameters.setCapacityIops(capacityIops);
    parameters.setHypervisorType(hypervisorType);
    parameters.setTags(tags);
    parameters.setDetails(details);

    final String managementVip = SolidFireUtil.getManagementVip(url);
    final int managementPort = SolidFireUtil.getManagementPort(url);

    details.put(SolidFireUtil.MANAGEMENT_VIP, managementVip);
    details.put(SolidFireUtil.MANAGEMENT_PORT, String.valueOf(managementPort));

    final String clusterAdminUsername = SolidFireUtil.getValue(SolidFireUtil.CLUSTER_ADMIN_USERNAME, url);
    final String clusterAdminPassword = SolidFireUtil.getValue(SolidFireUtil.CLUSTER_ADMIN_PASSWORD, url);

    details.put(SolidFireUtil.CLUSTER_ADMIN_USERNAME, clusterAdminUsername);
    details.put(SolidFireUtil.CLUSTER_ADMIN_PASSWORD, clusterAdminPassword);

    long lMinIops = 100;
    long lMaxIops = 15000;
    long lBurstIops = 15000;

    try {
      final String minIops = SolidFireUtil.getValue(SolidFireUtil.MIN_IOPS, url);

      if (minIops != null && minIops.trim().length() > 0) {
        lMinIops = Long.parseLong(minIops);
      }
    } catch (final Exception ex) {
      s_logger.info("[ignored]"
          + "error getting minimals iops: " + ex.getLocalizedMessage());
    }

    try {
      final String maxIops = SolidFireUtil.getValue(SolidFireUtil.MAX_IOPS, url);

      if (maxIops != null && maxIops.trim().length() > 0) {
        lMaxIops = Long.parseLong(maxIops);
      }
    } catch (final Exception ex) {
      s_logger.info("[ignored]"
          + "error getting maximal iops: " + ex.getLocalizedMessage());
    }

    try {
      final String burstIops = SolidFireUtil.getValue(SolidFireUtil.BURST_IOPS, url);

      if (burstIops != null && burstIops.trim().length() > 0) {
        lBurstIops = Long.parseLong(burstIops);
      }
    } catch (final Exception ex) {
      s_logger.info("[ignored]"
          + "error getting iops bursts: " + ex.getLocalizedMessage());
    }

    if (lMinIops > lMaxIops) {
      throw new CloudRuntimeException("The parameter '" + SolidFireUtil.MIN_IOPS + "' must be less than or equal to the parameter '" + SolidFireUtil.MAX_IOPS + "'.");
    }

    if (lMaxIops > lBurstIops) {
      throw new CloudRuntimeException("The parameter '" + SolidFireUtil.MAX_IOPS + "' must be less than or equal to the parameter '" + SolidFireUtil.BURST_IOPS + "'.");
    }

    if (lMinIops != capacityIops) {
      throw new CloudRuntimeException("The parameter '" + CAPACITY_IOPS + "' must be equal to the parameter '" + SolidFireUtil.MIN_IOPS + "'.");
    }

    if (lMinIops > SolidFireUtil.MAX_IOPS_PER_VOLUME || lMaxIops > SolidFireUtil.MAX_IOPS_PER_VOLUME || lBurstIops > SolidFireUtil.MAX_IOPS_PER_VOLUME) {
      throw new CloudRuntimeException("This volume cannot exceed " + NumberFormat.getInstance().format(SolidFireUtil.MAX_IOPS_PER_VOLUME) + " IOPS.");
    }

    details.put(SolidFireUtil.MIN_IOPS, String.valueOf(lMinIops));
    details.put(SolidFireUtil.MAX_IOPS, String.valueOf(lMaxIops));
    details.put(SolidFireUtil.BURST_IOPS, String.valueOf(lBurstIops));

    final SolidFireUtil.SolidFireConnection sfConnection = new SolidFireUtil.SolidFireConnection(managementVip, managementPort, clusterAdminUsername, clusterAdminPassword);

    final SolidFireCreateVolume sfCreateVolume = createSolidFireVolume(sfConnection, storagePoolName, capacityBytes, lMinIops, lMaxIops, lBurstIops);

    final SolidFireUtil.SolidFireVolume sfVolume = sfCreateVolume.getVolume();

    final String iqn = sfVolume.getIqn();

    details.put(SolidFireUtil.VOLUME_ID, String.valueOf(sfVolume.getId()));

    parameters.setUuid(iqn);
    parameters.setHost(storageVip);
    parameters.setPort(storagePort);
    parameters.setPath(iqn);

    // this adds a row in the cloud.storage_pool table for this SolidFire volume
    final DataStore dataStore = _primaryDataStoreHelper.createPrimaryDataStore(parameters);

    // now that we have a DataStore (we need the id from the DataStore instance), we can create a Volume Access Group, if need be, and
    // place the newly created volume in the Volume Access Group
    try {
      final List<HostVO> hosts = _hostDao.findByClusterId(clusterId);
      final ClusterVO cluster = _clusterDao.findById(clusterId);

      SolidFireUtil.placeVolumeInVolumeAccessGroup(sfConnection, sfVolume.getId(), dataStore.getId(), cluster.getUuid(), hosts, _clusterDetailsDao);

      final SolidFireUtil.SolidFireAccount sfAccount = sfCreateVolume.getAccount();
      final Account csAccount = CallContext.current().getCallingAccount();

      SolidFireUtil.updateCsDbWithSolidFireAccountInfo(csAccount.getId(), sfAccount, dataStore.getId(), _accountDetailsDao);
    } catch (final Exception ex) {
      _primaryDataStoreDao.expunge(dataStore.getId());

      throw new CloudRuntimeException(ex.getMessage());
    }

    return dataStore;
  }

  private HypervisorType getHypervisorTypeForCluster(long clusterId) {
    final ClusterVO cluster = _clusterDao.findById(clusterId);

    if (cluster == null) {
      throw new CloudRuntimeException("Cluster ID '" + clusterId + "' was not found in the database.");
    }

    return cluster.getHypervisorType();
  }

  private StoragePoolType getStorageType(HypervisorType hypervisorType) {
    if (HypervisorType.XenServer.equals(hypervisorType)) {
      return StoragePoolType.IscsiLUN;
    }

    throw new CloudRuntimeException("The 'hypervisor' parameter must be '" + HypervisorType.XenServer + "'.");
  }

  private class SolidFireCreateVolume {
    private final SolidFireUtil.SolidFireVolume _sfVolume;
    private final SolidFireUtil.SolidFireAccount _sfAccount;

    public SolidFireCreateVolume(SolidFireUtil.SolidFireVolume sfVolume, SolidFireUtil.SolidFireAccount sfAccount) {
      _sfVolume = sfVolume;
      _sfAccount = sfAccount;
    }

    public SolidFireUtil.SolidFireVolume getVolume() {
      return _sfVolume;
    }

    public SolidFireUtil.SolidFireAccount getAccount() {
      return _sfAccount;
    }
  }

  private SolidFireCreateVolume createSolidFireVolume(SolidFireUtil.SolidFireConnection sfConnection,
      String volumeName, long volumeSize, long minIops, long maxIops, long burstIops) {
    try {
      final Account csAccount = CallContext.current().getCallingAccount();
      final long csAccountId = csAccount.getId();
      final AccountVO accountVo = _accountDao.findById(csAccountId);

      final String sfAccountName = SolidFireUtil.getSolidFireAccountName(accountVo.getUuid(), csAccountId);

      SolidFireUtil.SolidFireAccount sfAccount = SolidFireUtil.getSolidFireAccount(sfConnection, sfAccountName);

      if (sfAccount == null) {
        final long sfAccountNumber = SolidFireUtil.createSolidFireAccount(sfConnection, sfAccountName);

        sfAccount = SolidFireUtil.getSolidFireAccountById(sfConnection, sfAccountNumber);
      }

      final long sfVolumeId = SolidFireUtil.createSolidFireVolume(sfConnection, SolidFireUtil.getSolidFireVolumeName(volumeName), sfAccount.getId(), volumeSize,
          true, null, minIops, maxIops, burstIops);
      final SolidFireUtil.SolidFireVolume sfVolume = SolidFireUtil.getSolidFireVolume(sfConnection, sfVolumeId);

      return new SolidFireCreateVolume(sfVolume, sfAccount);
    } catch (final Throwable e) {
      throw new CloudRuntimeException("Failed to create a SolidFire volume: " + e.toString());
    }
  }

  @Override
  public boolean attachHost(DataStore store, HostScope scope, StoragePoolInfo existingInfo) {
    return true;
  }

  @Override
  public boolean attachCluster(DataStore store, ClusterScope scope) {
    final PrimaryDataStoreInfo primaryDataStoreInfo = (PrimaryDataStoreInfo)store;

    // check if there is at least one host up in this cluster
    final List<HostVO> allHosts = _resourceMgr.listAllUpAndEnabledHosts(Host.Type.Routing, primaryDataStoreInfo.getClusterId(),
        primaryDataStoreInfo.getPodId(), primaryDataStoreInfo.getDataCenterId());

    if (allHosts.isEmpty()) {
      _primaryDataStoreDao.expunge(primaryDataStoreInfo.getId());

      throw new CloudRuntimeException("No host up to associate a storage pool with in cluster " + primaryDataStoreInfo.getClusterId());
    }

    boolean success = false;

    for (final HostVO host : allHosts) {
      success = createStoragePool(host, primaryDataStoreInfo);

      if (success) {
        break;
      }
    }

    if (!success) {
      throw new CloudRuntimeException("Unable to create storage in cluster " + primaryDataStoreInfo.getClusterId());
    }

    final List<HostVO> poolHosts = new ArrayList<HostVO>();

    for (final HostVO host : allHosts) {
      try {
        _storageMgr.connectHostToSharedPool(host.getId(), primaryDataStoreInfo.getId());

        poolHosts.add(host);
      } catch (final Exception e) {
        s_logger.warn("Unable to establish a connection between " + host + " and " + primaryDataStoreInfo, e);
      }
    }

    if (poolHosts.isEmpty()) {
      s_logger.warn("No host can access storage pool '" + primaryDataStoreInfo + "' on cluster '" + primaryDataStoreInfo.getClusterId() + "'.");

      _primaryDataStoreDao.expunge(primaryDataStoreInfo.getId());

      throw new CloudRuntimeException("Failed to access storage pool");
    }

    _primaryDataStoreHelper.attachCluster(store);

    return true;
  }

  private boolean createStoragePool(HostVO host, StoragePool storagePool) {
    final long hostId = host.getId();
    final CreateStoragePoolCommand cmd = new CreateStoragePoolCommand(true, storagePool);

    final Answer answer = _agentMgr.easySend(hostId, cmd);

    if (answer != null && answer.getResult()) {
      return true;
    } else {
      _primaryDataStoreDao.expunge(storagePool.getId());

      String msg = "";

      if (answer != null) {
        msg = "Cannot create storage pool through host '" + hostId + "' due to the following: " + answer.getDetails();
      } else {
        msg = "Cannot create storage pool through host '" + hostId + "' due to CreateStoragePoolCommand returns null";
      }

      s_logger.warn(msg);

      throw new CloudRuntimeException(msg);
    }
  }

  @Override
  public boolean attachZone(DataStore dataStore, ZoneScope scope, HypervisorType hypervisorType) {
    return true;
  }

  @Override
  public boolean maintain(DataStore dataStore) {
    _storagePoolAutomation.maintain(dataStore);
    _primaryDataStoreHelper.maintain(dataStore);

    return true;
  }

  @Override
  public boolean cancelMaintain(DataStore store) {
    _primaryDataStoreHelper.cancelMaintain(store);
    _storagePoolAutomation.cancelMaintain(store);

    return true;
  }

  // invoked to delete primary storage that is based on the SolidFire plug-in
  @Override
  public boolean deleteDataStore(DataStore dataStore) {
    final List<StoragePoolHostVO> hostPoolRecords = _storagePoolHostDao.listByPoolId(dataStore.getId());

    HypervisorType hypervisorType = null;

    if (hostPoolRecords.size() > 0 ) {
      hypervisorType = getHypervisorType(hostPoolRecords.get(0).getHostId());
    }

    if (!isSupportedHypervisorType(hypervisorType)) {
      throw new CloudRuntimeException(hypervisorType + " is not a supported hypervisor type.");
    }

    final StoragePool storagePool = (StoragePool)dataStore;
    final StoragePoolVO storagePoolVO = _primaryDataStoreDao.findById(storagePool.getId());
    final List<VMTemplateStoragePoolVO> unusedTemplatesInPool = _tmpltMgr.getUnusedTemplatesInPool(storagePoolVO);

    for (final VMTemplateStoragePoolVO templatePoolVO : unusedTemplatesInPool) {
      _tmpltMgr.evictTemplateFromStoragePool(templatePoolVO);
    }

    Long clusterId = null;

    for (final StoragePoolHostVO host : hostPoolRecords) {
      final DeleteStoragePoolCommand deleteCmd = new DeleteStoragePoolCommand(storagePool);
      final Answer answer = _agentMgr.easySend(host.getHostId(), deleteCmd);

      if (answer != null && answer.getResult()) {
        s_logger.info("Successfully deleted storage pool using Host ID " + host.getHostId());

        final HostVO hostVO = _hostDao.findById(host.getHostId());

        if (hostVO != null) {
          clusterId = hostVO.getClusterId();
        }

        break;
      }
      else {
        s_logger.error("Failed to delete storage pool using Host ID " + host.getHostId() + ": " + answer.getResult());
      }
    }

    if (clusterId != null) {
      removeVolumeFromVag(storagePool.getId(), clusterId);
    }

    deleteSolidFireVolume(storagePool.getId());

    return _primaryDataStoreHelper.deletePrimaryDataStore(dataStore);
  }

  private void removeVolumeFromVag(long storagePoolId, long clusterId) {
    final long sfVolumeId = getVolumeId(storagePoolId);
    final ClusterDetailsVO clusterDetail = _clusterDetailsDao.findDetail(clusterId, SolidFireUtil.getVagKey(storagePoolId));

    final String vagId = clusterDetail != null ? clusterDetail.getValue() : null;

    if (vagId != null) {
      final List<HostVO> hosts = _hostDao.findByClusterId(clusterId);

      final SolidFireUtil.SolidFireConnection sfConnection = SolidFireUtil.getSolidFireConnection(storagePoolId, _storagePoolDetailsDao);

      final SolidFireUtil.SolidFireVag sfVag = SolidFireUtil.getSolidFireVag(sfConnection, Long.parseLong(vagId));

      final String[] hostIqns = SolidFireUtil.getNewHostIqns(sfVag.getInitiators(), SolidFireUtil.getIqnsFromHosts(hosts));
      final long[] volumeIds = SolidFireUtil.getNewVolumeIds(sfVag.getVolumeIds(), sfVolumeId, false);

      SolidFireUtil.modifySolidFireVag(sfConnection, sfVag.getId(), hostIqns, volumeIds);
    }
  }

  private void deleteSolidFireVolume(long storagePoolId) {
    final SolidFireUtil.SolidFireConnection sfConnection = SolidFireUtil.getSolidFireConnection(storagePoolId, _storagePoolDetailsDao);

    final long sfVolumeId = getVolumeId(storagePoolId);

    SolidFireUtil.deleteSolidFireVolume(sfConnection, sfVolumeId);
  }

  private long getVolumeId(long storagePoolId) {
    final StoragePoolDetailVO storagePoolDetail = _storagePoolDetailsDao.findDetail(storagePoolId, SolidFireUtil.VOLUME_ID);

    final String volumeId = storagePoolDetail.getValue();

    return Long.parseLong(volumeId);
  }

  private long getIopsValue(long storagePoolId, String iopsKey) {
    final StoragePoolDetailVO storagePoolDetail = _storagePoolDetailsDao.findDetail(storagePoolId, iopsKey);

    final String iops = storagePoolDetail.getValue();

    return Long.parseLong(iops);
  }

  private static boolean isSupportedHypervisorType(HypervisorType hypervisorType) {
    return HypervisorType.XenServer.equals(hypervisorType);
  }

  private HypervisorType getHypervisorType(long hostId) {
    final HostVO host = _hostDao.findById(hostId);

    if (host != null) {
      return host.getHypervisorType();
    }

    return HypervisorType.None;
  }

  /* (non-Javadoc)
   * @see org.apache.cloudstack.engine.subsystem.api.storage.DataStoreLifeCycle#migrateToObjectStore(org.apache.cloudstack.engine.subsystem.api.storage.DataStore)
   */
  @Override
  public boolean migrateToObjectStore(DataStore store) {
    return false;
  }

  @Override
  public void updateStoragePool(StoragePool storagePool, Map<String, String> details) {
    final String strCapacityBytes = details.get(PrimaryDataStoreLifeCycle.CAPACITY_BYTES);
    final String strCapacityIops = details.get(PrimaryDataStoreLifeCycle.CAPACITY_IOPS);

    final Long capacityBytes = strCapacityBytes != null ? Long.parseLong(strCapacityBytes) : null;
    final Long capacityIops = strCapacityIops != null ? Long.parseLong(strCapacityIops) : null;

    final SolidFireUtil.SolidFireConnection sfConnection = SolidFireUtil.getSolidFireConnection(storagePool.getId(), _storagePoolDetailsDao);

    final long size = capacityBytes != null ? capacityBytes : storagePool.getCapacityBytes();

    final long currentMinIops = getIopsValue(storagePool.getId(), SolidFireUtil.MIN_IOPS);
    final long currentMaxIops = getIopsValue(storagePool.getId(), SolidFireUtil.MAX_IOPS);
    final long currentBurstIops = getIopsValue(storagePool.getId(), SolidFireUtil.BURST_IOPS);

    long minIops = currentMinIops;
    long maxIops = currentMaxIops;
    long burstIops = currentBurstIops;

    if (capacityIops != null) {
      if (capacityIops > SolidFireUtil.MAX_IOPS_PER_VOLUME) {
        throw new CloudRuntimeException("This volume cannot exceed " + NumberFormat.getInstance().format(SolidFireUtil.MAX_IOPS_PER_VOLUME) + " IOPS.");
      }

      final float maxPercentOfMin = currentMaxIops / (float)currentMinIops;
      final float burstPercentOfMax = currentBurstIops / (float)currentMaxIops;

      minIops = capacityIops;
      maxIops = (long)(minIops * maxPercentOfMin);
      burstIops = (long)(maxIops * burstPercentOfMax);

      if (maxIops > SolidFireUtil.MAX_IOPS_PER_VOLUME) {
        maxIops = SolidFireUtil.MAX_IOPS_PER_VOLUME;
      }

      if (burstIops > SolidFireUtil.MAX_IOPS_PER_VOLUME) {
        burstIops = SolidFireUtil.MAX_IOPS_PER_VOLUME;
      }
    }

    SolidFireUtil.modifySolidFireVolume(sfConnection, getVolumeId(storagePool.getId()), size, null, minIops, maxIops, burstIops);

    SolidFireUtil.updateCsDbWithSolidFireIopsInfo(storagePool.getId(), _primaryDataStoreDao, _storagePoolDetailsDao, minIops, maxIops, burstIops);
  }

  @Override
  public void enableStoragePool(DataStore dataStore) {
    _primaryDataStoreHelper.enable(dataStore);
  }

  @Override
  public void disableStoragePool(DataStore dataStore) {
    _primaryDataStoreHelper.disable(dataStore);
  }
}
