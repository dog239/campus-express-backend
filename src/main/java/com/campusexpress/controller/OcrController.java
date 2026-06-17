package com.campusexpress.controller;

import com.campusexpress.common.Result;
import com.campusexpress.service.OcrService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.Base64;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/ocr")
public class OcrController {

    private final OcrService ocrService;

    public OcrController(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(HttpServletRequest request) {
        try {
            String imageBase64 = null;
            
            // 尝试从 JSON body 获取
            String contentType = request.getContentType();
            System.out.println("=== Content-Type: " + contentType);
            
            if (contentType != null && contentType.contains("application/json")) {
                // JSON 格式
                @SuppressWarnings("unchecked")
                Map<String, String> body = (Map<String, String>) request.getAttribute("body");
                if (body != null) {
                    imageBase64 = body.get("imageBase64");
                }
            } else if (contentType != null && contentType.contains("multipart/form-data")) {
                // multipart 格式
                if (request instanceof MultipartHttpServletRequest) {
                    MultipartFile file = ((MultipartHttpServletRequest) request).getFile("imageFile");
                    if (file != null && !file.isEmpty()) {
                        imageBase64 = Base64.getEncoder().encodeToString(file.getBytes());
                    }
                }
                // 尝试从参数获取 base64
                if (imageBase64 == null) {
                    MultipartFile file = ((MultipartHttpServletRequest) request).getFile("imageBase64");
                    if (file != null && !file.isEmpty()) {
                        imageBase64 = Base64.getEncoder().encodeToString(file.getBytes());
                    }
                }
            }
            
            // 最后尝试从 request parameter 获取
            if (imageBase64 == null || imageBase64.trim().isEmpty()) {
                imageBase64 = request.getParameter("imageBase64");
            }
            
            System.out.println("=== imageBase64 是否存在: " + (imageBase64 != null && !imageBase64.trim().isEmpty()));
            
            if (imageBase64 == null || imageBase64.trim().isEmpty()) {
                return Result.error("请提供图片");
            }
            
            Map<String, Object> result = ocrService.extractPackageInfo(imageBase64);
            return Result.success(result);
        } catch (IllegalArgumentException ex) {
            return Result.error(ex.getMessage());
        } catch (Exception ex) {
            ex.printStackTrace();
            return Result.error("OCR 识别失败: " + ex.getMessage());
        }
    }
}
