MERGE INTO "user" ("id", "username", "password", "phone", "nickname", "avatar", "role", "deleted", "create_time", "update_time", "openid") KEY("id") VALUES
(1, 'demo_user', '123456', '13800138000', 'demo', '', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'test-openid-001');
