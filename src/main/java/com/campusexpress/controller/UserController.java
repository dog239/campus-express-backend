package com.campusexpress.controller;

import com.campusexpress.common.Result;
import com.campusexpress.entity.User;
import com.campusexpress.service.UserService;
import com.campusexpress.util.JwtUtil;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public UserController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        try {
            String code = request.get("code");
            return Result.success(userService.login(code));
        } catch (Exception ex) {
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
}
