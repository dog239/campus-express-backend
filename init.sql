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
    `code` varchar(32) NOT NULL COMMENT '取件码',
    `name` varchar(64) NOT NULL COMMENT '收件人姓名',
    `phone` varchar(11) NOT NULL COMMENT '收件人手机号',
    `address` varchar(128) NOT NULL COMMENT '收件地址',
    `type` tinyint NOT NULL COMMENT '快递类型 1:顺丰 2:圆通 3:中通 4:申通 5:韵达 6:邮政 7:京东',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态 0:待取件 1:已取件',
    `remark` varchar(255) DEFAULT '' COMMENT '备注',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 0:未删除 1:已删除',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='快递表';

-- 订单表
CREATE TABLE `orders` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `number` varchar(50) DEFAULT NULL COMMENT '订单号',
    `user_id` bigint NOT NULL COMMENT '用户id',
    `package_id` bigint NOT NULL COMMENT '快递id',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '订单状态 1:待付款 2:待接单 3:配送中 4:已完成 5:已取消',
    `amount` decimal(10,2) NOT NULL COMMENT '实收金额',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_number` (`number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';
