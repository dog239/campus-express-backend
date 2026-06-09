CREATE TABLE IF NOT EXISTS "user" (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `username` VARCHAR(32) NOT NULL DEFAULT '',
  `password` VARCHAR(128) NOT NULL DEFAULT '',
  `phone` VARCHAR(11) NOT NULL DEFAULT '',
  `nickname` VARCHAR(32) DEFAULT '',
  `avatar` VARCHAR(128) DEFAULT '',
  `role` TINYINT NOT NULL DEFAULT 0,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `openid` VARCHAR(64) DEFAULT ''
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_username ON "user"(username);
CREATE UNIQUE INDEX IF NOT EXISTS idx_phone ON "user"(phone);
CREATE UNIQUE INDEX IF NOT EXISTS idx_openid ON "user"(openid);

CREATE TABLE IF NOT EXISTS "package" (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id` BIGINT NOT NULL,
  `pickup_code` VARCHAR(32) NOT NULL,
  `station_name` VARCHAR(32) NOT NULL,
  `arrival_date` DATE NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 0,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_id ON "package"(user_id);
CREATE INDEX IF NOT EXISTS idx_station_name ON "package"(station_name);
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_pickup_code ON "package"(user_id, pickup_code);

CREATE TABLE IF NOT EXISTS "orders" (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `requester_id` BIGINT NOT NULL,
  `receiver_id` BIGINT,
  `package_ids` VARCHAR(255) NOT NULL,
  `station_name` VARCHAR(32),
  `tip_amount` DECIMAL(10, 2) NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 0,
  `requester_confirm` BOOLEAN DEFAULT FALSE,
  `receiver_confirm` BOOLEAN DEFAULT FALSE,
  `photo_url` VARCHAR(255),
  `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `complete_time` TIMESTAMP,
  `version` INT DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_requester_id ON "orders"(requester_id);
CREATE INDEX IF NOT EXISTS idx_receiver_id ON "orders"(receiver_id);
CREATE INDEX IF NOT EXISTS idx_status ON "orders"(status);

CREATE TABLE IF NOT EXISTS "warning_log" (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `package_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `warning_type` VARCHAR(64) NOT NULL,
  `warning_message` VARCHAR(255) NOT NULL,
  `pushed_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_warning_package_type ON "warning_log"(package_id, warning_type);
