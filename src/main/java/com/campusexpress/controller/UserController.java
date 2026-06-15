package com.campusexpress.controller;

import com.campusexpress.common.Result;
import com.campusexpress.entity.User;
import com.campusexpress.service.UserService;
import com.campusexpress.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@Tag(name = "User Management", description = "User APIs")
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public UserController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    @Operation(summary = "User login", description = "User login with WeChat code")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        try {
            String code = request.get("code");
            System.out.println("=== 收到登录请求，code: " + code);
            
            Map<String, Object> result = userService.login(code);
            System.out.println("=== 登录成功，返回结果: " + result);
            
            return Result.success(result);
        } catch (Exception ex) {
            System.err.println("=== 登录失败，错误: " + ex.getMessage());
            ex.printStackTrace();
            return Result.error(ex.getMessage());
        }
    }

    @GetMapping("/info")
    public Result<Map<String, Object>> info(@RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            if (authorization == null || authorization.isEmpty()) {
                return Result.error("缺少 token");
            }

            String openid = jwtUtil.parseToken(authorization);
            User user = userService.getUserByOpenid(openid);
            if (user == null) {
                return Result.error("用户不存在");
            }

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("username", user.getUsername());
            userInfo.put("nickname", user.getNickname());
            userInfo.put("avatar", user.getAvatar());
            userInfo.put("phone", user.getPhone());
            userInfo.put("role", user.getRole());

            return Result.success(userInfo);
        } catch (Exception ex) {
            return Result.error("token 无效: " + ex.getMessage());
        }
    }

    @PostMapping("/bindPhone")
    @Operation(summary = "Bind phone number", description = "Bind phone number for user")
    public Result<Map<String, String>> bindPhone(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, String> request) {
        try {
            System.out.println("=== 收到绑定手机号请求");
            
            if (authorization == null || authorization.isEmpty()) {
                return Result.error("缺少 token");
            }
            
            String openid = jwtUtil.parseToken(authorization);
            System.out.println("=== 解析 openid: " + openid);
            
            String encryptedData = request.get("encryptedData");
            String iv = request.get("iv");
            String code = request.get("code");
            String phone = request.get("phone");
            
            System.out.println("=== 请求参数: encryptedData=" + (encryptedData != null) + 
                             ", iv=" + (iv != null) + 
                             ", code=" + (code != null) +
                             ", phone=" + phone);
            
            String boundPhone;
            
            if (phone != null && !phone.trim().isEmpty()) {
                boundPhone = userService.bindPhoneDirect(openid, phone);
            } else if (encryptedData != null && iv != null && code != null) {
                boundPhone = userService.bindPhone(openid, encryptedData, iv, code);
            } else {
                return Result.error("请提供手机号或微信加密数据");
            }
            
            Map<String, String> result = new HashMap<>();
            result.put("phone", boundPhone);
            
            System.out.println("=== 绑定成功，手机号: " + boundPhone);
            return Result.success(result);
        } catch (Exception e) {
            System.err.println("=== 绑定手机号失败: " + e.getMessage());
            e.printStackTrace();
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/getPhoneMask")
    @Operation(summary = "Get phone number mask", description = "Get WeChat bound phone number mask (first 3 and last 4 digits)")
    public Result<Map<String, String>> getPhoneMask(@RequestBody Map<String, String> request) {
        try {
            System.out.println("=== 收到获取手机号掩码请求");
            
            String encryptedData = request.get("encryptedData");
            String iv = request.get("iv");
            String code = request.get("code");
            
            System.out.println("=== 请求参数: encryptedData=" + (encryptedData != null) + 
                             ", iv=" + (iv != null) + 
                             ", code=" + (code != null));
            
            if (encryptedData == null || iv == null || code == null) {
                return Result.error("缺少必要参数");
            }
            
            String phone = userService.decryptPhoneOnly(encryptedData, iv, code);
            System.out.println("=== 解密得到完整手机号: " + phone);
            
            String mask = phone.substring(0, 3) + "****" + phone.substring(7, 11);
            System.out.println("=== 生成掩码: " + mask);
            
            Map<String, String> result = new HashMap<>();
            result.put("mask", mask);
            result.put("phone", phone);
            
            return Result.success(result);
        } catch (Exception e) {
            System.err.println("=== 获取手机号掩码失败: " + e.getMessage());
            e.printStackTrace();
            return Result.error(e.getMessage());
        }
    }
}