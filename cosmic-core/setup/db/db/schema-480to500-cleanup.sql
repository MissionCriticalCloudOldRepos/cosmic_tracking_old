--;
-- Schema cleanup from 4.8.0 to 5.0.0;
--;

# Remove NetApp plugin
DROP TABLE IF EXISTS `cloud`.`netapp_volume`;
DROP TABLE IF EXISTS `cloud`.`netapp_pool`;
DROP TABLE IF EXISTS `cloud`.`netapp_lun`;

# Remove BigSwitch plugin
DROP TABLE IF EXISTS `cloud`.`external_bigswitch_bcf_devices`;
DROP TABLE IF EXISTS `cloud`.`external_bigswitch_vns_devices`;