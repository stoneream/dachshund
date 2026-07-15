CREATE DATABASE IF NOT EXISTS dachshund_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS dachshund_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

DROP USER IF EXISTS 'flyway-dev'@'%';
DROP USER IF EXISTS 'flyway-test'@'%';

CREATE USER IF NOT EXISTS 'dachshund_dev'@'%' IDENTIFIED BY 'dachshund_dev';
ALTER USER 'dachshund_dev'@'%' IDENTIFIED BY 'dachshund_dev';
GRANT ALL PRIVILEGES ON dachshund_dev.* TO 'dachshund_dev'@'%';

CREATE USER IF NOT EXISTS 'dachshund_test'@'%' IDENTIFIED BY 'dachshund_test';
ALTER USER 'dachshund_test'@'%' IDENTIFIED BY 'dachshund_test';
GRANT ALL PRIVILEGES ON dachshund_test.* TO 'dachshund_test'@'%';
