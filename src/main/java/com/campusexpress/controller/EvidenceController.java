package com.campusexpress.controller;

import com.campusexpress.common.Result;
import com.campusexpress.entity.User;
import com.campusexpress.service.EvidenceService;
import com.campusexpress.service.UserService;
import com.campusexpress.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/evidence")
@Tag(name = "Evidence Management", description = "Photo evidence APIs")
public class EvidenceController {

    private final EvidenceService evidenceService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    public EvidenceController(EvidenceService evidenceService, UserService userService, JwtUtil jwtUtil) {
        this.evidenceService = evidenceService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping(value = "/upload/{orderId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload evidence photo", description = "Upload delivery evidence photo, add watermark and store it")
    public Result<Map<String, Object>> uploadEvidence(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long orderId,
            @RequestParam("file") MultipartFile file) {
        try {
            User currentUser = getCurrentUser(authorization);
            EvidenceService.UploadResult uploadResult = evidenceService.uploadEvidence(orderId, currentUser.getId(), file);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("orderId", uploadResult.getOrderId());
            data.put("photoUrl", uploadResult.getPhotoUrl());
            data.put("viewUrl", uploadResult.getViewUrl());
            return Result.success(data);
        } catch (Exception ex) {
            return Result.error(ex.getMessage());
        }
    }

    @GetMapping("/view/{orderId}")
    @Operation(summary = "View evidence photo", description = "View the uploaded evidence photo by order ID")
    public ResponseEntity<byte[]> viewEvidence(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long orderId) {
        User currentUser = getCurrentUser(authorization);
        EvidenceService.EvidenceFile evidenceFile = evidenceService.viewEvidence(orderId, currentUser.getId());
        MediaType mediaType = MediaType.parseMediaType(evidenceFile.getContentType());

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(mediaType)
                .body(evidenceFile.getContent());
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
}
