--;
-- Schema cleanup from 4.8.0 to 5.0.0;
--;

# Remove NetApp plugin
DROP TABLE IF EXISTS `cloud`.`netapp_lun`;
DROP TABLE IF EXISTS `cloud`.`netapp_volume`;
DROP TABLE IF EXISTS `cloud`.`netapp_pool`;

# Remove BigSwitch plugin
DROP TABLE IF EXISTS `cloud`.`external_bigswitch_vns_devices`;
DROP TABLE IF EXISTS `cloud`.`external_bigswitch_bcf_devices`;

# Remove Brocade plugin
DROP TABLE IF EXISTS `cloud`.`brocade_network_vlan_map`;
DROP TABLE IF EXISTS `cloud`.`external_brocade_vcs_devices`;

# Remove HyperV and VMware templates
DELETE FROM `cloud`.`vm_template` WHERE hypervisor_type = 'Hyperv';
DELETE FROM `cloud`.`vm_template` WHERE hypervisor_type = 'VMware';
DELETE FROM `cloud`.`hypervisor_capabilities` WHERE hypervisor_type = 'Hyperv';
DELETE FROM `cloud`.`hypervisor_capabilities` WHERE hypervisor_type = 'VMware';

# Remove HyperV and VMware global settings
DELETE FROM `cloud`.`configuration` WHERE value = 'hyperv.guest.network.device';
DELETE FROM `cloud`.`configuration` WHERE value = 'hyperv.private.network.device';
DELETE FROM `cloud`.`configuration` WHERE value = 'hyperv.public.network.device';
DELETE FROM `cloud`.`configuration` WHERE value = 'router.template.hyperv';
DELETE FROM `cloud`.`configuration` WHERE value = 'router.template.vmware';

# Remove HyperV related column on physical_network_traffic_types table
ALTER TABLE `cloud`.`physical_network_traffic_types` DROP COLUMN `hyperv_network_label`;