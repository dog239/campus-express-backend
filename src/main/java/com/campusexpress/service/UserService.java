package com.campusexpress.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campusexpress.config.WechatProperties;
import com.campusexpress.entity.User;
import com.campusexpress.mapper.UserMapper;
import com.campusexpress.util.JwtUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final WechatProperties wechatProperties;

    private final RestTemplate restTemplate = new RestTemplate();

    public UserService(UserMapper userMapper, JwtUtil jwtUtil, WechatProperties wechatProperties) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.wechatProperties = wechatProperties;
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

        String url = String.format("%s?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                wechatProperties.getSessionUrl(),
                wechatProperties.getAppid(),
                wechatProperties.getSecret(),
                code);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        if (response == null) {
            throw new IllegalStateException("微信登录返回为空");
        }

        if (response.containsKey("errcode") && response.get("errcode") != null) {
            throw new IllegalStateException("微信登录失败: " + response.get("errmsg"));
        }

        Object openid = response.get("openid");
        if (openid == null) {
            throw new IllegalStateException("微信登录失败，未获取到 openid");
        }
        return String.valueOf(openid);
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
}
