--;
-- Schema cleanup from 5.0.1 to 5.1.0;
--;
# Remove OpenDayLight plugin
DROP TABLE IF EXISTS `cloud`.`external_opendaylight_controllers`;
