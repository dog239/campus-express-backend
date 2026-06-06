package com.campusexpress.controller;

import com.campusexpress.common.Result;
import com.campusexpress.service.OcrService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ocr")
public class OcrController {

    private final OcrService ocrService;

    public OcrController(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(value = "imageBase64", required = false) String imageBase64,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile
    ) {
        try {
            if (body != null && (imageBase64 == null || imageBase64.trim().isEmpty())) {
                imageBase64 = body.get("imageBase64");
            }

            if ((imageBase64 == null || imageBase64.trim().isEmpty()) && imageFile != null && !imageFile.isEmpty()) {
                imageBase64 = Base64.getEncoder().encodeToString(imageFile.getBytes());
            }

            List<String> codes = ocrService.extractCodes(imageBase64);
            Map<String, Object> response = new HashMap<>();
            response.put("codes", codes);
            return Result.success(response);
        } catch (IllegalArgumentException ex) {
            return Result.error(ex.getMessage());
        } catch (Exception ex) {
            return Result.error("OCR 识别失败: " + ex.getMessage());
        }
    }
}
