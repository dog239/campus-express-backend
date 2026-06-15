# Debug 模式修复说明

## 问题原因

**Invalid AES key length: 19 bytes** 错误的根本原因：

1. `application.yml` 中配置了 `wechat.debug: true`
2. `getSessionKey()` 方法检测到 debug 模式后，返回假的 `mock_session_key_for_debug`
3. 这个假的 session_key 长度不正确，导致 AES 解密失败

---

## 修复内容

### 1. 修改 application.yml

**修改前**：
```yaml
wechat:
  appid: wxf75e60b7eab2499b
  secret: c7ae0fb7819190c6f8e019afd34b9379
  session-url: https://api.weixin.qq.com/sns/jscode2session
  access-token-url: https://api.weixin.qq.com/cgi-bin/token
  subscribe-send-url: https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=%s
  debug: true  # ← 问题所在！
```

**修改后**：
```yaml
wechat:
  appid: wxf75e60b7eab2499b
  secret: c7ae0fb7819190c6f8e019afd34b9379
  session-url: https://api.weixin.qq.com/sns/jscode2session
  access-token-url: https://api.weixin.qq.com/cgi-bin/token
  subscribe-send-url: https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=%s
  debug: false  # ← 关闭 Debug 模式
```

---

### 2. 修改 getSessionKey 方法

**修改前（错误的 Debug 代码）**：
```java
private String getSessionKey(String code) {
    if (wechatProperties.isDebug()) {
        System.out.println("=== Debug 模式，返回模拟 session_key");
        return "mock_session_key_for_debug";  // ← 假的 session_key！
    }
    
    // 真实调用微信接口...
}
```

**修改后（正确代码）**：
```java
private String getSessionKey(String code) {
    System.out.println("=== 开始获取 session_key");
    System.out.println("=== code: " + code);
    System.out.println("=== appid: " + wechatProperties.getAppid());
    
    String url = String.format("%s?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
            wechatProperties.getSessionUrl(),
            wechatProperties.getAppid(),
            wechatProperties.getSecret(),
            code);
    
    System.out.println("=== 请求微信接口（隐藏 secret）: " + 
            url.replace(wechatProperties.getSecret(), "SECRET"));
    
    try {
        String response = restTemplate.getForObject(url, String.class);
        System.out.println("=== 微信接口返回: " + response);
        
        JSONObject json = JSONObject.parseObject(response);
        
        if (json.containsKey("session_key")) {
            String sessionKey = json.getString("session_key");
            String openid = json.getString("openid");
            
            System.out.println("=== 获取 session_key 成功");
            System.out.println("=== session_key 原始值: " + sessionKey);
            System.out.println("=== session_key 长度: " + sessionKey.length());
            System.out.println("=== openid: " + openid);
            
            return sessionKey;
        } else if (json.containsKey("errcode")) {
            Integer errcode = json.getInteger("errcode");
            String errmsg = json.getString("errmsg");
            
            System.err.println("=== 微信接口错误");
            System.err.println("=== errcode: " + errcode);
            System.err.println("=== errmsg: " + errmsg);
            
            // 常见错误码说明
            if (errcode == -1) {
                System.err.println("=== 系统繁忙，请稍后重试");
            } else if (errcode == 40029) {
                System.err.println("=== code 无效（可能已过期或已使用）");
            } else if (errcode == 45011) {
                System.err.println("=== 频率限制，每个用户每分钟100次");
            }
            
            throw new RuntimeException("获取 session_key 失败: errcode=" + errcode + ", errmsg=" + errmsg);
        } else {
            System.err.println("=== 未知错误，响应格式不正确");
            throw new RuntimeException("未知错误: " + response);
        }
    } catch (Exception e) {
        System.err.println("=== 获取 session_key 异常: " + e.getMessage());
        e.printStackTrace();
        throw new RuntimeException("获取 session_key 失败: " + e.getMessage(), e);
    }
}
```

---

## 完整的 bindPhone 方法流程

```java
public String bindPhone(String openid, String encryptedData, String iv, String code) {
    System.out.println("=== 开始绑定手机号");
    System.out.println("=== openid: " + openid);
    System.out.println("=== code: " + code);
    
    // 1. 获取 session_key（真实调用微信接口）
    String sessionKey = getSessionKey(code);
    System.out.println("=== 获取 session_key 成功: " + sessionKey);
    
    // 2. 解密手机号
    String phone = decryptPhoneNumber(encryptedData, sessionKey, iv);
    System.out.println("=== 解密手机号成功: " + phone);
    
    // 3. 保存到数据库
    userMapper.update(null, new UpdateWrapper<User>()
            .eq("openid", openid)
            .set("phone", phone)
            .set("update_time", LocalDateTime.now()));
    
    return phone;
}
```

---

## 必要的 import 语句

```java
import com.alibaba.fastjson.JSONObject;
import org.springframework.web.client.RestTemplate;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
```

---

## RestTemplate 配置

**方式 1：在类中直接创建（当前使用）**：
```java
private final RestTemplate restTemplate = new RestTemplate();
```

**方式 2：通过 Spring 注入（推荐）**：
```java
@Autowired
private RestTemplate restTemplate;

// 或在配置类中创建 Bean
@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

---

## 测试步骤

### 1. 重启后端服务
```bash
cd D:\NewDelivery\campus-express-backend
mvn spring-boot:run
```

### 2. 测试绑定手机号
1. 微信开发者工具导入小程序
2. 登录后进入"我的"页面
3. 点击"快速绑定"按钮
4. 点击"允许"
5. 查看后端控制台日志

---

## 预期日志输出

### 正常情况：
```
=== 收到绑定手机号请求
=== Authorization: 有
=== 解析 openid: oXXXX...
=== 请求参数:
    encryptedData: 有，长度=96
    iv: 有，长度=24
    code: 有，值=071ABC...
=== 使用微信解密方式
=== 开始绑定手机号
=== openid: oXXXX...
=== code: 071ABC...
=== 开始获取 session_key
=== code: 071ABC...
=== appid: wxf75e60b7eab2499b
=== 请求微信接口（隐藏 secret）: https://api.weixin.qq.com/sns/jscode2session?appid=wxf75e60b7eab2499b&secret=SECRET&js_code=071ABC...&grant_type=authorization_code
=== 微信接口返回: {"session_key":"xxx24位Base64字符串xxx","openid":"oXXXX..."}

=== 获取 session_key 成功
=== session_key 原始值: xxx24位Base64字符串xxx
=== session_key 长度: 24
=== openid: oXXXX...

=== 开始解密手机号
=== 原始 sessionKey: xxx24位Base64字符串xxx
=== 原始 sessionKey 长度: 24
=== 清理后 sessionKey 长度: 24
=== Base64 解码成功
=== sessionKeyBytes 长度: 24 bytes  ← 正确！
=== ivBytes 长度: 16 bytes
=== 解密后的数据: {"phoneNumber":"13800138000","purePhoneNumber":"13800138000",...}
=== 最终手机号: 13800138000
=== 解密手机号成功: 13800138000
=== 绑定成功，手机号: 13800138000
```

### 错误情况（code 无效）：
```
=== 开始获取 session_key
=== code: 071ABC...
=== 微信接口返回: {"errcode":40029,"errmsg":"invalid code"}
=== 微信接口错误
=== errcode: 40029
=== errmsg: invalid code
=== code 无效（可能已过期或已使用）
=== 获取 session_key 失败: errcode=40029, errmsg=invalid code
```

---

## 常见错误码说明

| errcode | errmsg | 说明 | 解决方案 |
|---------|--------|------|---------|
| -1 | system error | 系统繁忙 | 稍后重试 |
| 40029 | invalid code | code 无效 | 重新调用 wx.login() |
| 45011 | rate limit | 频率限制 | 每个用户每分钟最多 100 次 |
| 40163 | code been used | code 已使用 | 每次绑定都重新获取 code |
| 40001 | invalid credential | appid 或 secret 错误 | 检查配置 |

---

## 关键点总结

| 修复项 | 说明 |
|--------|------|
| ✅ 关闭 Debug 模式 | `application.yml` 中 `debug: false` |
| ✅ 删除 mock 代码 | 不返回假的 session_key |
| ✅ 真实调用微信接口 | 使用 RestTemplate 调用 jscode2session |
| ✅ 详细日志输出 | 打印 session_key 长度、微信接口返回等 |
| ✅ 错误处理 | 处理各种 errcode，给出明确提示 |

---

## 验证修复成功

修复成功的标志：

1. ✅ 后端日志显示 `session_key 长度: 24`
2. ✅ 后端日志显示 `sessionKeyBytes 长度: 24 bytes`
3. ✅ 后端日志显示 `解密后的数据: {"phoneNumber":"13800138000",...}`
4. ✅ 数据库中保存了正确的手机号

---

**修复完成！** 现在重启后端服务，测试绑定手机号功能应该可以正常工作了。
