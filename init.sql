DROP DATABASE IF EXISTS campus_express;

CREATE DATABASE campus_express CHARACTER SET utf8mb4;

USE campus_express;

-- 用户表
CREATE TABLE `user` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username` varchar(32) NOT NULL DEFAULT '' COMMENT '用户名',
    `password` varchar(128) NOT NULL DEFAULT '' COMMENT '密码',
    `phone` varchar(11) NOT NULL DEFAULT '' COMMENT '手机号',
    `nickname` varchar(32) DEFAULT '' COMMENT '昵称',
    `avatar` varchar(128) DEFAULT '' COMMENT '头像',
    `role` tinyint NOT NULL DEFAULT 0 COMMENT '角色 1:管理员 0:普通用户',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 0:未删除 1:已删除',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `openid` varchar(64) DEFAULT '' COMMENT '微信 openid',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_username` (`username`),
    UNIQUE KEY `idx_phone` (`phone`),
    UNIQUE KEY `idx_openid` (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

INSERT INTO `user` (`username`, `password`, `phone`, `nickname`, `avatar`, `role`, `openid`, `create_time`, `update_time`)
VALUES ('demo_user', '123456', '13800138000', 'demo', '', 0, 'test-openid-001', NOW(), NOW());

-- 快递表
CREATE TABLE `package` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `pickup_code` varchar(32) NOT NULL COMMENT '取件码',
    `station_name` varchar(32) NOT NULL COMMENT '驿站名称',
    `arrival_date` date NOT NULL COMMENT '到站日期',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '快递状态 0:未取件 1:已取件',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 0:未删除 1:已删除',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_station_name` (`station_name`),
    UNIQUE KEY `idx_user_pickup_code` (`user_id`, `pickup_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='快递表';

-- 订单表
CREATE TABLE `orders` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `requester_id` bigint NOT NULL COMMENT '发布者ID',
    `receiver_id` bigint DEFAULT NULL COMMENT '接单者ID',
    `package_ids` varchar(255) NOT NULL COMMENT '快递ID列表，逗号分隔',
    `station_name` varchar(32) DEFAULT NULL COMMENT '驿站名称',
    `tip_amount` decimal(10,2) NOT NULL COMMENT '小费金额',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '订单状态 0:待接单 1:已接单 2:已完成 3:已取消',
    `requester_confirm` tinyint(1) DEFAULT 0 COMMENT '发布者是否确认完成',
    `receiver_confirm` tinyint(1) DEFAULT 0 COMMENT '接单者是否确认完成',
    `photo_url` varchar(255) DEFAULT NULL COMMENT '存证照片路径',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `complete_time` datetime DEFAULT NULL COMMENT '完成时间',
    `version` int DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_requester_id` (`requester_id`),
    KEY `idx_receiver_id` (`receiver_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- 预警日志表
CREATE TABLE `warning_log` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `package_id` bigint NOT NULL COMMENT '快递ID',
    `user_id` bigint NOT NULL COMMENT '接收预警的用户ID',
    `warning_type` varchar(64) NOT NULL COMMENT '预警类型',
    `warning_message` varchar(255) NOT NULL COMMENT '预警内容',
    `pushed_at` datetime DEFAULT NULL COMMENT '推送时间',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_warning_package_type` (`package_id`, `warning_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='滞留预警日志表';
