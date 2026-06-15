package com.campusexpress.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// @Component // 已禁用：改用 schema.sql 和 data.sql 初始化数据库
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        createTables();
        insertTestData();
    }

    private void createTables() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS `user` (" +
                "ID BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "USERNAME VARCHAR(32) NOT NULL DEFAULT '', " +
                "PASSWORD VARCHAR(128) NOT NULL DEFAULT '', " +
                "PHONE VARCHAR(11) NOT NULL DEFAULT '', " +
                "NICKNAME VARCHAR(32) DEFAULT '', " +
                "AVATAR VARCHAR(128) DEFAULT '', " +
                "ROLE TINYINT NOT NULL DEFAULT 0, " +
                "DELETED TINYINT NOT NULL DEFAULT 0, " +
                "UPDATE_TIME TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "CREATE_TIME TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "OPENID VARCHAR(64) DEFAULT ''" +
                ")");

        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS IDX_USERNAME ON `user`(USERNAME)");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS IDX_PHONE ON `user`(PHONE)");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS IDX_OPENID ON `user`(OPENID)");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS `package` (" +
                "ID BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "USER_ID BIGINT NOT NULL, " +
                "PICKUP_CODE VARCHAR(32) NOT NULL, " +
                "STATION_NAME VARCHAR(32) NOT NULL, " +
                "ARRIVAL_DATE DATE NOT NULL, " +
                "STATUS TINYINT NOT NULL DEFAULT 0, " +
                "DELETED TINYINT NOT NULL DEFAULT 0, " +
                "UPDATE_TIME TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "CREATE_TIME TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")");

        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS IDX_USER_ID ON `package`(USER_ID)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS IDX_STATION_NAME ON `package`(STATION_NAME)");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS `orders` (" +
                "ID BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "REQUESTER_ID BIGINT NOT NULL, " +
                "RECEIVER_ID BIGINT, " +
                "PACKAGE_IDS VARCHAR(255) NOT NULL, " +
                "STATION_NAME VARCHAR(32), " +
                "TIP_AMOUNT DECIMAL(10, 2) NOT NULL, " +
                "STATUS TINYINT NOT NULL DEFAULT 0, " +
                "REQUESTER_CONFIRM BOOLEAN DEFAULT FALSE, " +
                "RECEIVER_CONFIRM BOOLEAN DEFAULT FALSE, " +
                "PHOTO_URL VARCHAR(255), " +
                "CREATE_TIME TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "COMPLETE_TIME TIMESTAMP, " +
                "VERSION INT DEFAULT 0" +
                ")");

        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS IDX_REQUESTER_ID ON `orders`(REQUESTER_ID)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS IDX_RECEIVER_ID ON `orders`(RECEIVER_ID)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS IDX_STATUS ON `orders`(STATUS)");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS `warning_log` (" +
                "ID BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "PACKAGE_ID BIGINT NOT NULL, " +
                "USER_ID BIGINT NOT NULL, " +
                "WARNING_TYPE VARCHAR(64) NOT NULL, " +
                "WARNING_MESSAGE VARCHAR(255) NOT NULL, " +
                "PUSHED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "CREATE_TIME TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS IDX_WARNING_PACKAGE_TYPE ON `warning_log`(PACKAGE_ID, WARNING_TYPE)");
    }

    private void insertTestData() {
        jdbcTemplate.execute("MERGE INTO `user` (ID, USERNAME, PASSWORD, PHONE, NICKNAME, AVATAR, ROLE, DELETED, CREATE_TIME, UPDATE_TIME, OPENID) " +
                "KEY(ID) VALUES (1, 'demo_user', '123456', '13800138000', 'demo', '', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'test-openid-001')");

        jdbcTemplate.execute("MERGE INTO `user` (ID, USERNAME, PASSWORD, PHONE, NICKNAME, AVATAR, ROLE, DELETED, CREATE_TIME, UPDATE_TIME, OPENID) " +
                "KEY(ID) VALUES (2, 'debug_user', '123456', '13900139000', '微信用户', '', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'wx_debug-openid')");

        jdbcTemplate.execute("MERGE INTO `user` (ID, USERNAME, PASSWORD, PHONE, NICKNAME, AVATAR, ROLE, DELETED, CREATE_TIME, UPDATE_TIME, OPENID) " +
                "KEY(ID) VALUES (3, 'debug-test-user', '123456', '13000130000', '调试用户', '', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'debug-openid')");

        jdbcTemplate.execute("MERGE INTO `package` (ID, USER_ID, PICKUP_CODE, STATION_NAME, ARRIVAL_DATE, STATUS, DELETED, CREATE_TIME, UPDATE_TIME) " +
                "KEY(ID) VALUES (1, 1, '123456', '妈妈驿站', '2026-06-01', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

        jdbcTemplate.execute("MERGE INTO `package` (ID, USER_ID, PICKUP_CODE, STATION_NAME, ARRIVAL_DATE, STATUS, DELETED, CREATE_TIME, UPDATE_TIME) " +
                "KEY(ID) VALUES (2, 1, '654321', '近邻宝', '2026-06-02', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    }
}
