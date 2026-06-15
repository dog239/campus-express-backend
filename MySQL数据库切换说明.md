# MySQL 数据库切换说明

## 一、修改内容总结

### 1. application.yml - 数据源配置

**修改前（H2）**：
```yaml
datasource:
  driver-class-name: org.h2.Driver
  url: jdbc:h2:mem:campus_express;...
  username: sa
  password:
```

**修改后（MySQL）**：
```yaml
datasource:
  driver-class-name: com.mysql.cj.jdbc.Driver
  url: jdbc:mysql://10.25.111.156:3306/campus_express?useSSL=false&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true
  username: root
  password: Ma131419+
```

**URL 参数说明**：
- `useSSL=false`：禁用 SSL（内网不需要）
- `serverTimezone=Asia/Shanghai`：设置时区
- `createDatabaseIfNotExist=true`：自动创建数据库
- `allowPublicKeyRetrieval=true`：允许公钥检索（MySQL 8.0+ 需要）

---

### 2. pom.xml - MySQL 驱动依赖

**添加依赖**：
```xml
<!-- MySQL Driver -->
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>8.0.33</version>
    <scope>runtime</scope>
</dependency>
```

---

### 3. MyBatis-Plus 配置

**修改前**：
```yaml
db-type: h2
```

**修改后**：
```yaml
db-type: mysql
```

---

### 4. schema.sql - MySQL 兼容语法

当前的 `schema.sql` 已经是 MySQL 兼容的语法，无需修改：

- ✅ `AUTO_INCREMENT`：MySQL 支持
- ✅ `TIMESTAMP DEFAULT CURRENT_TIMESTAMP()`：MySQL 支持
- ✅ `BOOLEAN`：MySQL 支持（映射到 TINYINT(1)）
- ✅ `CREATE TABLE IF NOT EXISTS`：MySQL 支持
- ✅ `CREATE INDEX IF NOT EXISTS`：MySQL 8.0+ 支持

---

## 二、完整的 application.yml

```yaml
server:
  port: 8080

spring:
  application:
    name: campus-express-backend
  
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://10.25.111.156:3306/campus_express?useSSL=false&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true
    username: root
    password: Ma131419+
  
  sql:
    init:
      mode: always
      schema-locations: classpath:db/schema.sql
      # data-locations: classpath:db/data.sql

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    cache-enabled: false
    call-setters-on-nulls: true
    jdbc-type-for-null: 'null'
  global-config:
    banner: false
    db-config:
      id-type: AUTO
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
      table-underline: true
      db-type: mysql
  mapper-locations: classpath:mapper/*.xml

jwt:
  secret: campus-express-secret-key-2024-!@#abcXYZ
  expiration: 604800

wechat:
  appid: wxf75e60b7eab2499b
  secret: c7ae0fb7819190c6f8e019afd34b9379
  session-url: https://api.weixin.qq.com/sns/jscode2session
  access-token-url: https://api.weixin.qq.com/cgi-bin/token
  subscribe-send-url: https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=%s
  debug: false

obs:
  enabled: ${OBS_ENABLED:false}
  endpoint: obs.cn-north-4.myhuaweicloud.com
  access-key-id: ${OBS_ACCESS_KEY_ID:}
  secret-access-key: ${OBS_SECRET_ACCESS_KEY:}
  bucket-name: picture-64
  folder: campus-express/evidence
  url-prefix:
  local-dir: uploads/evidence

warning:
  webhook-url:
  scan-cron: 0 0 2 * * ?
  wechat:
    enabled: false
    template-id:
    page: pages/package/list/index
    miniprogram-state: formal
    lang: zh_CN
    title-field: thing1
    station-field: thing2
    code-field: character_string3
    date-field: time4
    remark-field: thing5

baidu:
  ocr:
    api-key: DTfR5EqnYhihCDUZ5mzuF5Ek
    secret-key: rffIP39mmOoFF13tnpZHZ4IFYBiOqkHj
    token-url: https://aip.baidubce.com/oauth/2.0/token
    general-url: https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic
```

---

## 三、数据库连接信息

| 参数 | 值 |
|------|------|
| 内网地址 | 10.25.111.156:3306 |
| 用户名 | root |
| 密码 | Ma131419+ |
| 数据库名 | campus_express |
| 驱动 | com.mysql.cj.jdbc.Driver |

---

## 四、部署步骤

### 1. 本地测试（可选）

如果本地无法连接云托管内网 MySQL，可以暂时切回 H2：

```yaml
datasource:
  driver-class-name: org.h2.Driver
  url: jdbc:h2:mem:campus_express;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;NON_KEYWORDS=USER
  username: sa
  password:
```

### 2. 微信云托管部署

1. **确认 MySQL 已创建**：
   - 微信云托管 → 数据库 → MySQL
   - 确认数据库实例已创建
   - 确认内网地址：10.25.111.156:3306

2. **提交代码到 GitHub**：
   ```bash
   git add .
   git commit -m "切换到云托管 MySQL"
   git push
   ```

3. **重新部署服务**：
   - 微信云托管 → 服务管理 → 重新部署
   - 查看构建日志
   - 查看运行日志

4. **验证数据库连接**：
   - 查看启动日志，应该显示：
     ```
     HikariPool-1 - Start completed.
     ```
   - 不应该出现连接错误

---

## 五、常见问题

### Q1: 本地开发无法连接 MySQL

**原因**：MySQL 内网地址只能在云托管内部访问

**解决方案**：
1. 本地开发时使用 H2 数据库
2. 部署时切换到 MySQL
3. 或使用 SSH 隧道连接云托管 MySQL

---

### Q2: 部署后连接失败

**错误信息**：
```
Communications link failure
```

**原因**：
1. MySQL 地址错误
2. MySQL 未启动
3. 网络不通

**解决**：
1. 确认内网地址正确：10.25.111.156:3306
2. 确认 MySQL 实例已启动
3. 确认服务与 MySQL 在同一网络

---

### Q3: 认证失败

**错误信息**：
```
Access denied for user 'root'@'...'
```

**原因**：用户名或密码错误

**解决**：
1. 确认用户名：root
2. 确认密码：Ma131419+
3. 检查 MySQL 用户权限

---

### Q4: 数据库不存在

**错误信息**：
```
Unknown database 'campus_express'
```

**原因**：数据库未创建

**解决**：
1. URL 中已添加 `createDatabaseIfNotExist=true`，会自动创建
2. 或手动创建数据库：
   ```sql
   CREATE DATABASE campus_express;
   ```

---

### Q5: Public Key Retrieval 不允许

**错误信息**：
```
Public Key Retrieval is not allowed
```

**原因**：MySQL 8.0+ 的认证机制

**解决**：
URL 中已添加 `allowPublicKeyRetrieval=true`

---

## 六、数据持久化

### H2 vs MySQL 对比

| 特性 | H2 | MySQL |
|------|------|-------|
| 数据持久化 | ❌ 内存数据库，重启丢失 | ✅ 持久化存储 |
| 本地访问 | ✅ 可以 | ❌ 需要内网 |
| 生产环境 | ❌ 不推荐 | ✅ 推荐 |
| 数据备份 | ❌ 无 | ✅ 支持 |

---

## 七、切换回 H2（本地开发）

如果需要切回 H2 进行本地开发：

```yaml
spring:
  datasource:
    driver-class-name: org.h2.Driver
    url: jdbc:h2:mem:campus_express;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;NON_KEYWORDS=USER
    username: sa
    password:
  
  h2:
    console:
      enabled: true
      path: /h2-console

mybatis-plus:
  global-config:
    db-config:
      db-type: h2
```

---

## 八、验证成功标志

部署成功后，应该看到：

```
Started CampusExpressApplication in X.XXX seconds
HikariPool-1 - Start completed.
```

**不应该出现**：
```
Communications link failure
Access denied for user
Unknown database
```

---

**配置完成！** 现在可以部署到微信云托管，使用云托管的 MySQL 数据库。
