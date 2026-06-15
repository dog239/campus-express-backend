package com.campusexpress.controller;

import com.campusexpress.common.Result;
import com.campusexpress.entity.User;
import com.campusexpress.service.PackageService;
import com.campusexpress.service.UserService;
import com.campusexpress.util.JwtUtil;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/package")
public class PackageController {

    private final PackageService packageService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    public PackageController(PackageService packageService, UserService userService, JwtUtil jwtUtil) {
        this.packageService = packageService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/add")
    public Result<String> addPackage(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, String> request
    ) {
        try {
            User currentUser = getCurrentUser(authorization);
            LocalDate arrivalDate = parseArrivalDate(request.get("arrivalDate"));
            packageService.addPackage(
                    currentUser.getId(),
                    request.get("pickupCode"),
                    request.get("stationName"),
                    arrivalDate
            );
            return Result.success("录入成功");
        } catch (Exception ex) {
            return Result.error(ex.getMessage());
        }
    }

    @GetMapping("/list")
    public Result<Map<String, List<Map<String, Object>>>> listPackages(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        try {
            User currentUser = getCurrentUser(authorization);
            return Result.success(packageService.listMyPackages(currentUser.getId()));
        } catch (Exception ex) {
            return Result.error(ex.getMessage());
        }
    }

    private User getCurrentUser(String authorization) {
        if (authorization == null || authorization.isEmpty()) {
            throw new IllegalArgumentException("缺少 token");
        }

        String openid = jwtUtil.parseToken(authorization);
        User user = userService.getUserByOpenid(openid);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return user;
    }

    private LocalDate parseArrivalDate(String arrivalDate) {
        if (arrivalDate == null || arrivalDate.trim().isEmpty()) {
            throw new IllegalArgumentException("arrivalDate 不能为空");
        }

        try {
            return LocalDate.parse(arrivalDate.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("arrivalDate 格式错误，请使用 yyyy-MM-dd");
        }
    }

    @DeleteMapping("/{packageId}")
    public Result<String> deletePackage(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long packageId) {
        try {
            User currentUser = getCurrentUser(authorization);
            packageService.deletePackage(packageId, currentUser.getId());
            return Result.success("删除成功");
        } catch (Exception ex) {
            return Result.error(ex.getMessage());
        }
    }
}
