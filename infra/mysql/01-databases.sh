#!/bin/bash
# Runs once, on the first start of the MySQL container. One database and one user per service:
# each user can reach only its own database.
set -euo pipefail

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS order_intake     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS order_management CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS notification     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'order_intake'@'%'     IDENTIFIED BY '${ORDER_INTAKE_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'order_management'@'%' IDENTIFIED BY '${ORDER_MANAGEMENT_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'notification'@'%'     IDENTIFIED BY '${NOTIFICATION_DB_PASSWORD}';

GRANT ALL PRIVILEGES ON order_intake.*     TO 'order_intake'@'%';
GRANT ALL PRIVILEGES ON order_management.* TO 'order_management'@'%';
GRANT ALL PRIVILEGES ON notification.*     TO 'notification'@'%';
FLUSH PRIVILEGES;
SQL
