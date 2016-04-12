UPDATE `configuration` SET `scope` = 'Zone' where `name` = 'blacklisted.routes';

# Update Hypervisor list in order to remove LXC
UPDATE configuration SET value='KVM,XenServer,BareMetal' WHERE name='hypervisor.list';
