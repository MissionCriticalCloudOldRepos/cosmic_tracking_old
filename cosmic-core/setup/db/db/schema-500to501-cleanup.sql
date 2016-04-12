--;
-- Schema cleanup from 5.0.0 to 5.0.1;
--;

# Remove LXC templates
DELETE FROM `cloud`.`vm_template` WHERE hypervisor_type = 'LXC';
DELETE FROM `cloud`.`hypervisor_capabilities` WHERE hypervisor_type = 'LXC';

# Remove LXC global settings
DELETE FROM `cloud`.`configuration` WHERE name = 'router.template.lxc';

# Remove LXC related column on physical_network_traffic_types table
ALTER TABLE `cloud`.`physical_network_traffic_types` DROP COLUMN `lxc_network_label`;

# Remove LXC
DELETE FROM `cloud`.`guest_os_hypervisor` where hypervisor_type = 'LXC';
