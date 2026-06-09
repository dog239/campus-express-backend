MERGE INTO "user" ("id", "username", "password", "phone", "nickname", "avatar", "role", "deleted", "create_time", "update_time", "openid") KEY("id") VALUES
(1, 'demo_user', '123456', '13800138000', 'demo', '', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'test-openid-001');

MERGE INTO "package" ("id", "user_id", "pickup_code", "station_name", "arrival_date", "status", "deleted", "create_time", "update_time") KEY("id") VALUES
(1, 1, '123456', '妈妈驿站', DATE '2026-06-01', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 1, '654321', '近邻宝', DATE '2026-06-02', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
