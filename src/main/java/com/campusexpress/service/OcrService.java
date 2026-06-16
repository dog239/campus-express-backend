package com.campusexpress.service;

import com.campusexpress.config.OcrProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OcrService {

    private static final Pattern CODE_PATTERN = Pattern.compile(
            "\\b(?:[A-Z]?\\d{2,6}|\\d{4,8}|[A-Z]\\d{4,8}|\\d+-\\d+-?\\d*|\\d{4,8}-[A-Z]?\\d{2,4})\\b",
            Pattern.CASE_INSENSITIVE
    );

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

    public Map<String, Object> extractPackageInfo(String imageBase64) {
        if (imageBase64 == null || imageBase64.trim().isEmpty()) {
            throw new IllegalArgumentException("请提供 imageBase64 或 imageFile");
        }

        String normalizedImage = normalizeBase64(imageBase64);
        List<String> textLines = callBaiduOcr(normalizedImage);
        
        Set<String> codes = parseCodes(textLines);
        String station = extractStation(textLines);
        
        Map<String, Object> result = new HashMap<>();
        result.put("codes", new ArrayList<>(codes));
        result.put("station", station);
        
        if (!codes.isEmpty()) {
            result.put("code", codes.iterator().next());
        }
        
        return result;
    }

    private String extractStation(List<String> lines) {
        String fullText = String.join("", lines);
        
        // 1. 匹配「到XXX」或「已到XXX」
        Matcher arriveMatch = Pattern.compile("(?:到|已到|到达|已送达)\\s*([^，,。！？]+)").matcher(fullText);
        if (arriveMatch.find()) {
            String result = arriveMatch.group(1).trim();
            // 检查是否包含驿站关键词
            String[] stationKeywords = {"京东服务站", "妈妈驿站", "近邻宝", "菜鸟驿站", "顺丰驿站", "丰巢", "驿小哥", "京东驿站"};
            for (String kw : stationKeywords) {
                if (result.contains(kw)) {
                    int idx = result.indexOf(kw) + kw.length();
                    return result.substring(0, idx);
                }
            }
            if (result.length() > 2 && result.length() <= 30) {
                return result;
            }
        }
        
        // 2. 匹配关键词
        String[] keywords = {"妈妈驿站", "近邻宝院内", "近邻宝", "菜鸟驿站", "京东服务站", "顺丰驿站", "丰巢快递柜", "丰巢", "驿小哥", "京东驿站"};
        for (String kw : keywords) {
            if (fullText.contains(kw)) {
                return kw;
            }
        }
        
        return "未知驿站";
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

        try {
            String responseStr = restTemplate.getForObject(url, String.class);
            System.out.println("百度 OCR token 接口返回: " + responseStr);

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> response = mapper.readValue(responseStr, Map.class);

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
        } catch (Exception e) {
            throw new RuntimeException("解析百度 OCR token 响应失败: " + e.getMessage(), e);
        }
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
            // 先尝试匹配取件码关键词
            String[] keywords = {"取件码", "取货码", "凭", "尾号", "验证码"};
            for (String kw : keywords) {
                Pattern p = Pattern.compile(kw + "\\s*[:：]?\\s*([A-Z0-9\\-]+)", Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(line);
                if (m.find()) {
                    String code = m.group(1);
                    if (code.length() >= 2 && code.length() <= 20) {
                        result.add(code);
                    }
                    break;
                }
            }
            // 再用通用正则匹配
            Matcher matcher = CODE_PATTERN.matcher(line);
            while (matcher.find()) {
                String code = matcher.group();
                // 过滤掉太短或太长的
                if (code.length() >= 2 && code.length() <= 20) {
                    result.add(code);
                }
            }
        }
        return result;
    }
}
