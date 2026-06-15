SET IDENTITY_INSERT [user] ON;
IF NOT EXISTS (SELECT 1 FROM [user] WHERE [id] = 1)
INSERT INTO [user] ([id], [username], [password], [phone], [nickname], [avatar], [role], [deleted], [create_time], [update_time], [openid])
VALUES (1, 'demo_user', '123456', '13800138000', 'demo', '', 0, 0, GETDATE(), GETDATE(), 'test-openid-001');
SET IDENTITY_INSERT [user] OFF;

SET IDENTITY_INSERT [package] ON;
IF NOT EXISTS (SELECT 1 FROM [package] WHERE [id] = 1)
INSERT INTO [package] ([id], [user_id], [pickup_code], [station_name], [arrival_date], [status], [deleted], [create_time], [update_time])
VALUES (1, 1, '123456', '妈妈驿站', '2026-06-01', 0, 0, GETDATE(), GETDATE());
IF NOT EXISTS (SELECT 1 FROM [package] WHERE [id] = 2)
INSERT INTO [package] ([id], [user_id], [pickup_code], [station_name], [arrival_date], [status], [deleted], [create_time], [update_time])
VALUES (2, 1, '654321', '近邻宝', '2026-06-02', 0, 0, GETDATE(), GETDATE());
SET IDENTITY_INSERT [package] OFF;
