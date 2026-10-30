#!/bin/bash
# Runs once, on the first start of the MySQL container. One database and one user per service:
# each user can reach only its own database.
set -euo pipefail

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS order_intake     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS order_approval CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS care             CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'order_intake'@'%'     IDENTIFIED BY '${ORDER_INTAKE_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'order_approval'@'%' IDENTIFIED BY '${ORDER_APPROVAL_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'care'@'%'             IDENTIFIED BY '${CARE_DB_PASSWORD}';

GRANT ALL PRIVILEGES ON order_intake.*     TO 'order_intake'@'%';
GRANT ALL PRIVILEGES ON order_approval.* TO 'order_approval'@'%';
GRANT ALL PRIVILEGES ON care.*             TO 'care'@'%';
FLUSH PRIVILEGES;
SQL
