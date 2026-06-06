package com.campusexpress.service;

import com.campusexpress.config.OcrProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OcrService {

    private static final Pattern CODE_PATTERN = Pattern.compile("\\b\\d{6,8}\\b");

    private final OcrProperties ocrProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    private String accessToken;
    private long accessTokenExpireAt;

    public OcrService(OcrProperties ocrProperties) {
        this.ocrProperties = ocrProperties;
    }

    public List<String> extractCodes(String imageBase64) {
        if (imageBase64 == null || imageBase64.trim().isEmpty()) {
            throw new IllegalArgumentException("请提供 imageBase64 或 imageFile");
        }

        String normalizedImage = normalizeBase64(imageBase64);
        List<String> textLines = callBaiduOcr(normalizedImage);
        Set<String> codes = parseCodes(textLines);

        if (codes.isEmpty()) {
            throw new IllegalStateException("未识别到取件码，请确认图片清晰、完整，并重试");
        }

        return new ArrayList<>(codes);
    }

    private String normalizeBase64(String imageBase64) {
        String base64 = imageBase64.trim();
        int commaIndex = base64.indexOf(",");
        if (base64.startsWith("data:") && commaIndex > 0) {
            base64 = base64.substring(commaIndex + 1);
        }
        return base64.replaceAll("\\s+", "");
    }

    private List<String> callBaiduOcr(String imageBase64) {
        String token = getAccessToken();
        String url = ocrProperties.getGeneralUrl() + "?access_token=" + urlEncode(token);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("image", imageBase64);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

        if (response == null) {
            throw new IllegalStateException("百度 OCR 返回为空");
        }
        if (response.containsKey("error_code")) {
            Object errorCode = response.get("error_code");
            Object errorMsg = response.get("error_msg");
            throw new IllegalStateException("百度 OCR 失败: " + errorCode + " " + errorMsg);
        }

        Object wordsResult = response.get("words_result");
        if (!(wordsResult instanceof List)) {
            throw new IllegalStateException("百度 OCR 返回格式异常");
        }

        List<String> lines = new ArrayList<>();
        for (Object item : (List<?>) wordsResult) {
            if (item instanceof Map) {
                Object words = ((Map<?, ?>) item).get("words");
                if (words != null) {
                    lines.add(String.valueOf(words));
                }
            }
        }

        return lines;
    }

    private synchronized String getAccessToken() {
        long now = System.currentTimeMillis();
        if (accessToken != null && now + 60_000 < accessTokenExpireAt) {
            return accessToken;
        }

        if (ocrProperties.getApiKey() == null || ocrProperties.getApiKey().trim().isEmpty()
                || ocrProperties.getSecretKey() == null || ocrProperties.getSecretKey().trim().isEmpty()) {
            throw new IllegalStateException("百度 OCR 配置未完成，请在 application.yml 中配置 baidu.ocr.api-key 和 baidu.ocr.secret-key");
        }

        String url = String.format("%s?grant_type=client_credentials&client_id=%s&client_secret=%s",
                ocrProperties.getTokenUrl(),
                urlEncode(ocrProperties.getApiKey()),
                urlEncode(ocrProperties.getSecretKey())
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        if (response == null) {
            throw new IllegalStateException("百度 OCR token 获取失败，返回为空");
        }
        if (response.containsKey("error")) {
            throw new IllegalStateException("百度 OCR token 获取失败: " + response.get("error_description"));
        }

        Object tokenObj = response.get("access_token");
        Object expireObj = response.get("expires_in");
        if (tokenObj == null || expireObj == null) {
            throw new IllegalStateException("百度 OCR token 响应缺少 access_token 或 expires_in");
        }

        accessToken = String.valueOf(tokenObj);
        long expiresIn;
        if (expireObj instanceof Number) {
            expiresIn = ((Number) expireObj).longValue();
        } else {
            expiresIn = Long.parseLong(String.valueOf(expireObj));
        }
        accessTokenExpireAt = now + expiresIn * 1000L;
        return accessToken;
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new IllegalStateException("URL 编码失败", e);
        }
    }

    private Set<String> parseCodes(List<String> lines) {
        Set<String> result = new LinkedHashSet<>();
        for (String line : lines) {
            Matcher matcher = CODE_PATTERN.matcher(line);
            while (matcher.find()) {
                result.add(matcher.group());
            }
        }
        return result;
    }
}
