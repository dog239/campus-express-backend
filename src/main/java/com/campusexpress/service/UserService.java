package com.campusexpress.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.campusexpress.config.WechatProperties;
import com.campusexpress.entity.User;
import com.campusexpress.mapper.UserMapper;
import com.campusexpress.util.JwtUtil;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final WechatProperties wechatProperties;
    private final EvidenceStorageService evidenceStorageService;

    private final RestTemplate restTemplate = new RestTemplate();

    public UserService(UserMapper userMapper, JwtUtil jwtUtil, WechatProperties wechatProperties, EvidenceStorageService evidenceStorageService) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.wechatProperties = wechatProperties;
        this.evidenceStorageService = evidenceStorageService;
    }

    private void disableSSLVerification() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Map<String, Object> login(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("code 不能为空");
        }

        String openid = resolveOpenid(code);

        User user = userMapper.selectOne(new QueryWrapper<User>().eq("openid", openid));
        if (user == null) {
            user = createUser(openid);
        }

        String token = jwtUtil.generateToken(openid);

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("userInfo", buildUserInfo(user));
        return response;
    }

    public User getUserByOpenid(String openid) {
        return userMapper.selectOne(new QueryWrapper<User>().eq("openid", openid));
    }

    private String resolveOpenid(String code) {
        if (wechatProperties.isDebug() && "debug-test".equals(code)) {
            return "debug-openid";
        }
        if (wechatProperties.isDebug() && "demo-test".equals(code)) {
            return "test-openid-001";
        }

        disableSSLVerification();

        String url = String.format("%s?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                wechatProperties.getSessionUrl(),
                wechatProperties.getAppid(),
                wechatProperties.getSecret(),
                code);

        try {
            String response = restTemplate.getForObject(url, String.class);
            System.out.println("微信接口返回: " + response);

            JSONObject json = JSONObject.parseObject(response);

            if (json.containsKey("openid")) {
                return json.getString("openid");
            } else if (json.containsKey("errcode")) {
                throw new RuntimeException("微信接口错误: " + response);
            } else {
                throw new RuntimeException("未知错误: " + response);
            }
        } catch (Exception e) {
            throw new RuntimeException("解析微信响应失败: " + e.getMessage(), e);
        }
    }

    private User createUser(String openid) {
        User user = new User();
        user.setOpenid(openid);
        user.setUsername("wx_" + openid.substring(0, Math.min(12, openid.length())));
        user.setPassword("");
        user.setPhone("");
        user.setNickname("微信用户");
        user.setAvatar("");
        user.setRole(0);
        user.setDeleted(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        userMapper.insert(user);
        return user;
    }

    private Map<String, Object> buildUserInfo(User user) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        info.put("nickname", user.getNickname());
        info.put("avatar", user.getAvatar());
        info.put("phone", user.getPhone());
        info.put("role", user.getRole());
        return info;
    }

    public String bindPhoneDirect(String openid, String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("手机号不能为空");
        }
        
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
        
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("openid", openid));
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        
        userMapper.update(null, new UpdateWrapper<User>()
                .eq("openid", openid)
                .set("phone", phone)
                .set("update_time", LocalDateTime.now()));
        
        return phone;
    }

    public String bindPhone(String openid, String encryptedData, String iv, String code) {
        System.out.println("=== 开始绑定手机号，openid: " + openid);
        
        String sessionKey = getSessionKey(code);
        System.out.println("=== 获取 session_key 成功");
        
        String phone = decryptPhoneNumber(encryptedData, sessionKey, iv);
        System.out.println("=== 解密手机号成功: " + phone);
        
        userMapper.update(null, new UpdateWrapper<User>()
                .eq("openid", openid)
                .set("phone", phone)
                .set("update_time", LocalDateTime.now()));
        
        return phone;
    }

    public String decryptPhoneOnly(String encryptedData, String iv, String code) {
        System.out.println("=== 开始解密手机号（仅获取，不保存）");
        
        String sessionKey = getSessionKey(code);
        System.out.println("=== 获取 session_key 成功");
        
        String phone = decryptPhoneNumber(encryptedData, sessionKey, iv);
        System.out.println("=== 解密手机号成功: " + phone);
        
        return phone;
    }

    private String getSessionKey(String code) {
        System.out.println("=== 开始获取 session_key");
        System.out.println("=== code: " + code);
        System.out.println("=== appid: " + wechatProperties.getAppid());
        
        disableSSLVerification();
        
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

    private String decryptPhoneNumber(String encryptedData, String sessionKey, String iv) {
        try {
            System.out.println("=== 开始解密手机号");
            System.out.println("=== 原始 sessionKey: " + sessionKey);
            System.out.println("=== 原始 sessionKey 长度: " + sessionKey.length());
            System.out.println("=== 原始 iv: " + iv);
            System.out.println("=== 原始 iv 长度: " + iv.length());
            System.out.println("=== 原始 encryptedData 长度: " + encryptedData.length());
            
            // 清理 Base64 字符串
            String cleanedSessionKey = cleanBase64(sessionKey);
            String cleanedIv = cleanBase64(iv);
            String cleanedEncryptedData = cleanBase64(encryptedData);
            
            System.out.println("=== 清理后 sessionKey: " + cleanedSessionKey);
            System.out.println("=== 清理后 sessionKey 长度: " + cleanedSessionKey.length());
            System.out.println("=== 清理后 iv 长度: " + cleanedIv.length());
            System.out.println("=== 清理后 encryptedData 长度: " + cleanedEncryptedData.length());
            
            // Base64 解码
            byte[] sessionKeyBytes = Base64.getDecoder().decode(cleanedSessionKey);
            byte[] ivBytes = Base64.getDecoder().decode(cleanedIv);
            byte[] encryptedBytes = Base64.getDecoder().decode(cleanedEncryptedData);
            
            System.out.println("=== Base64 解码成功");
            System.out.println("=== sessionKeyBytes 长度: " + sessionKeyBytes.length + " bytes");
            System.out.println("=== ivBytes 长度: " + ivBytes.length + " bytes");
            System.out.println("=== encryptedBytes 长度: " + encryptedBytes.length + " bytes");
            
            // 验证 AES Key 长度（必须是 16、24 或 32 字节）
            if (sessionKeyBytes.length != 16 && sessionKeyBytes.length != 24 && sessionKeyBytes.length != 32) {
                System.err.println("=== 错误：sessionKeyBytes 长度 " + sessionKeyBytes.length + " 不是有效的 AES key 长度");
                System.err.println("=== sessionKeyBytes 内容: " + bytesToHex(sessionKeyBytes));
                throw new RuntimeException("无效的 session_key，长度: " + sessionKeyBytes.length + " bytes");
            }
            
            // 验证 IV 长度（必须是 16 字节）
            if (ivBytes.length != 16) {
                System.err.println("=== 错误：ivBytes 长度 " + ivBytes.length + " 不是有效的 IV 长度");
                throw new RuntimeException("无效的 iv，长度: " + ivBytes.length + " bytes");
            }
            
            // AES 解密
            SecretKeySpec keySpec = new SecretKeySpec(sessionKeyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            
            byte[] decrypted = cipher.doFinal(encryptedBytes);
            String decryptedStr = new String(decrypted, StandardCharsets.UTF_8);
            
            System.out.println("=== 解密后的数据: " + decryptedStr);
            
            // 解析 JSON
            JSONObject json = JSONObject.parseObject(decryptedStr);
            
            String phone = json.getString("phoneNumber");
            if (phone == null) {
                phone = json.getString("purePhoneNumber");
            }
            
            // 去掉国家代码前缀（如果有+86）
            if (phone != null && phone.startsWith("+86")) {
                phone = phone.substring(3);
            }
            
            if (phone == null) {
                throw new RuntimeException("解密数据中未找到手机号");
            }
            
            System.out.println("=== 最终手机号: " + phone);
            return phone;
        } catch (Exception e) {
            System.err.println("=== 解密失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("解密手机号失败: " + e.getMessage(), e);
        }
    }
    
    private String cleanBase64(String base64Str) {
        if (base64Str == null || base64Str.isEmpty()) {
            throw new IllegalArgumentException("Base64 字符串不能为空");
        }
        
        // 1. 移除所有空白字符（空格、换行、制表符等）
        String cleaned = base64Str.replaceAll("\\s+", "");
        
        // 2. URL-safe Base64 转标准 Base64
        // 微信使用 URL-safe 编码：_ 替代 /，- 替代 +
        cleaned = cleaned.replace('_', '/').replace('-', '+');
        
        // 3. 补齐 Base64 长度（必须是 4 的倍数）
        int padding = 4 - (cleaned.length() % 4);
        if (padding != 4) {
            for (int i = 0; i < padding; i++) {
                cleaned += "=";
            }
        }
        
        return cleaned;
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    public void updateNickname(String openid, String nickname) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("openid", openid));
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        
        userMapper.update(null, new UpdateWrapper<User>()
                .eq("openid", openid)
                .set("nickname", nickname)
                .set("update_time", LocalDateTime.now()));
    }

    public Map<String, Object> getUserStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();
        
        Integer packageCount = userMapper.countPackagesByUserId(userId);
        Integer orderCount = userMapper.countOrdersByUserId(userId);
        String earnings = userMapper.sumEarningsByUserId(userId);
        
        stats.put("packageCount", packageCount != null ? packageCount : 0);
        stats.put("orderCount", orderCount != null ? orderCount : 0);
        stats.put("earnings", earnings != null ? earnings : "0.00");
        
        return stats;
    }

    public String uploadAvatar(String openid, MultipartFile file) {
        try {
            User user = userMapper.selectOne(new QueryWrapper<User>().eq("openid", openid));
            if (user == null) {
                throw new IllegalArgumentException("用户不存在");
            }
            
            String fileName = "avatar/" + openid + "/" + System.currentTimeMillis() + ".jpg";
            String objectKey = evidenceStorageService.buildObjectKey(fileName);
            byte[] content = file.getBytes();
            String contentType = file.getContentType();
            if (contentType == null || contentType.isEmpty()) {
                contentType = "image/jpeg";
            }
            
            evidenceStorageService.upload(objectKey, content, contentType);
            
            String avatarUrl = objectKey;
            
            userMapper.update(null, new UpdateWrapper<User>()
                    .eq("openid", openid)
                    .set("avatar", avatarUrl)
                    .set("update_time", LocalDateTime.now()));
            
            return avatarUrl;
        } catch (Exception e) {
            throw new RuntimeException("上传头像失败: " + e.getMessage(), e);
        }
    }

    public ResponseEntity<byte[]> getAvatar(String openid) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("openid", openid));
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        
        String avatarPath = user.getAvatar();
        if (avatarPath == null || avatarPath.isEmpty()) {
            throw new IllegalArgumentException("用户未设置头像");
        }
        
        EvidenceStorageService.StoredFile storedFile = evidenceStorageService.download(avatarPath);
        String contentType = storedFile.getContentType();
        if (contentType == null || contentType.isEmpty()) {
            contentType = "image/jpeg";
        }
        
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(storedFile.getContent());
    }
}
