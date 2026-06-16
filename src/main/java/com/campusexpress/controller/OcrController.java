package com.campusexpress.controller;

import com.campusexpress.common.Result;
import com.campusexpress.service.OcrService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/ocr")
public class OcrController {

    private final OcrService ocrService;

    public OcrController(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<Map<String, Object>> uploadByBase64(@RequestBody Map<String, String> body) {
        try {
            String imageBase64 = body.get("imageBase64");
            if (imageBase64 == null || imageBase64.trim().isEmpty()) {
                return Result.error("请提供图片");
            }
            Map<String, Object> result = ocrService.extractPackageInfo(imageBase64);
            return Result.success(result);
        } catch (IllegalArgumentException ex) {
            return Result.error(ex.getMessage());
        } catch (Exception ex) {
            return Result.error("OCR 识别失败: " + ex.getMessage());
        }
    }

    @PostMapping(value = "/uploadFile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> uploadByFile(@RequestParam("imageFile") MultipartFile imageFile) {
        try {
            if (imageFile == null || imageFile.isEmpty()) {
                return Result.error("请提供图片文件");
            }
            String imageBase64 = Base64.getEncoder().encodeToString(imageFile.getBytes());
            Map<String, Object> result = ocrService.extractPackageInfo(imageBase64);
            return Result.success(result);
        } catch (IllegalArgumentException ex) {
            return Result.error(ex.getMessage());
        } catch (Exception ex) {
            return Result.error("OCR 识别失败: " + ex.getMessage());
        }
    }
}
