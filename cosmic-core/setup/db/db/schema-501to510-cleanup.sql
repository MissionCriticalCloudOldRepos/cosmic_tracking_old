--;
-- Schema cleanup from 5.0.1 to 5.1.0;
--;
# Remove OpenDayLight plugin
DROP TABLE IF EXISTS `cloud`.`external_opendaylight_controllers`;

# Remove IAM plugin
DROP TABLE IF EXISTS `cloud`.`iam_policy_permission`;
DROP TABLE IF EXISTS `cloud`.`iam_account_policy_map`;
DROP TABLE IF EXISTS `cloud`.`iam_group_policy_map`;
DROP TABLE IF EXISTS `cloud`.`iam_policy`;
DROP TABLE IF EXISTS `cloud`.`iam_group_account_map`;
DROP TABLE IF EXISTS `cloud`.`iam_group`;
