package com.campusexpress.controller;

import com.campusexpress.common.Result;
import com.campusexpress.service.OcrService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.Map;

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
            
            String contentType = request.getContentType();
            System.out.println("=== Content-Type: " + contentType);
            
            if (contentType != null && contentType.contains("multipart/form-data")) {
                if (request instanceof MultipartHttpServletRequest) {
                    MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
                    
                    MultipartFile file = multipartRequest.getFile("imageFile");
                    if (file != null && !file.isEmpty()) {
                        System.out.println("=== 从 imageFile 获取图片");
                        imageBase64 = Base64.getEncoder().encodeToString(file.getBytes());
                    }
                    
                    if (imageBase64 == null) {
                        file = multipartRequest.getFile("imageBase64");
                        if (file != null && !file.isEmpty()) {
                            System.out.println("=== 从 imageBase64 file 获取图片");
                            imageBase64 = Base64.getEncoder().encodeToString(file.getBytes());
                        }
                    }
                }
            }
            
            if (imageBase64 == null || imageBase64.trim().isEmpty()) {
                imageBase64 = request.getParameter("imageBase64");
                if (imageBase64 != null) {
                    System.out.println("=== 从 parameter 获取 imageBase64");
                }
            }
            
            System.out.println("=== imageBase64 是否存在: " + (imageBase64 != null && !imageBase64.trim().isEmpty()));
            if (imageBase64 != null) {
                System.out.println("=== imageBase64 长度: " + imageBase64.length());
            }
            
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
