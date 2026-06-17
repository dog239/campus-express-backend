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
            "\\b(?:\\d{1,2}-\\d{1,2}-\\d{3,5}|\\d{1,2}-\\d{3,5}|[A-Z]?\\d{4,8}|[A-Z]\\d{4,8})\\b",
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
        String fullText = String.join("\n", lines);
        
        // 1. 优先匹配「取件地址：」或「地址：」后的内容
        Pattern addrPattern = Pattern.compile("(?:取件地址|地址)\\s*[:：]?\\s*(.+?)(?:\n|$)");
        Matcher addrMatcher = addrPattern.matcher(fullText);
        if (addrMatcher.find()) {
            String address = addrMatcher.group(1).trim();
            // 清理末尾的无关字符
            address = address.replaceAll("[，,。！？\\s]+$", "");
            if (address.length() >= 5 && address.length() <= 100) {
                return address;
            }
        }
        
        // 2. 匹配「到XXX」或「已到XXX」
        Matcher arriveMatch = Pattern.compile("(?:到|已到|到达|已送达)\\s*([^，,。！？\n]+)").matcher(fullText);
        if (arriveMatch.find()) {
            String result = arriveMatch.group(1).trim();
            String[] stationKeywords = {"京东服务站", "妈妈驿站", "近邻宝", "菜鸟驿站", "顺丰驿站", "丰巢", "驿小哥", "京东驿站"};
            for (String kw : stationKeywords) {
                if (result.contains(kw)) {
                    int idx = result.indexOf(kw) + kw.length();
                    return result.substring(0, idx);
                }
            }
            if (result.length() > 2 && result.length() <= 50) {
                return result;
            }
        }
        
        // 3. 匹配驿站关键词并提取完整名称
        String[] keywords = {"妈妈驿站", "近邻宝院内", "近邻宝", "菜鸟驿站", "京东服务站", "顺丰驿站", "丰巢快递柜", "丰巢", "驿小哥", "京东驿站"};
        for (String kw : keywords) {
            int idx = fullText.indexOf(kw);
            if (idx >= 0) {
                // 尝试向前提取地址（最多20个字符）
                int start = Math.max(0, idx - 20);
                String before = fullText.substring(start, idx);
                // 找到地址起始点（取件地址、地址、到、已到等关键词）
                Pattern startPattern = Pattern.compile("(?:取件地址|地址|到|已到)[:：]?\\s*$");
                Matcher startMatcher = startPattern.matcher(before);
                if (startMatcher.find()) {
                    return fullText.substring(start + startMatcher.start(), idx + kw.length()).trim();
                }
                // 如果没有找到起始关键词，返回关键词本身
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
        String fullText = String.join("\n", lines);
        
        // 优先级1：匹配关键词后的取件码（支持连字符格式）
        String[] keywords = {"取货码", "取件码", "凭", "尾号", "验证码"};
        for (String kw : keywords) {
            Pattern p = Pattern.compile(kw + "\\s*[:：]?\\s*([A-Z0-9]+(?:-[A-Z0-9]+)+)", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(fullText);
            while (m.find()) {
                String code = m.group(1).trim();
                result.add(code);
            }
        }
        
        // 优先级2：直接匹配连字符格式（2834-2569、7-1-1914、S-2-2095）
        if (result.isEmpty()) {
            Pattern hyphenPattern = Pattern.compile("\\b([A-Z0-9]+(?:-[A-Z0-9]+)+)\\b", Pattern.CASE_INSENSITIVE);
            Matcher m = hyphenPattern.matcher(fullText);
            while (m.find()) {
                String code = m.group(1).trim();
                if (isValidCode(code)) {
                    result.add(code);
                }
            }
        }
        
        // 如果已找到连字符格式的取件码，直接返回
        if (!result.isEmpty()) {
            return result;
        }
        
        // 优先级3：匹配关键词后的普通取件码
        for (String kw : keywords) {
            Pattern p = Pattern.compile(kw + "\\s*[:：]?\\s*([A-Z0-9]{4,10})", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(fullText);
            while (m.find()) {
                String code = m.group(1).trim();
                if (isValidCode(code)) {
                    result.add(code);
                }
            }
        }
        
        // 优先级4：通用正则匹配（排除时间格式）
        if (result.isEmpty()) {
            for (String line : lines) {
                if (line.matches(".*\\d{1,2}:\\d{2}.*") || line.contains("取件时间") || line.contains("营业时间")) {
                    continue;
                }
                Matcher matcher = CODE_PATTERN.matcher(line);
                while (matcher.find()) {
                    String code = matcher.group();
                    if (isValidCode(code)) {
                        result.add(code);
                    }
                }
            }
        }
        return result;
    }
    
    private boolean isValidCode(String code) {
        if (code == null || code.length() < 3 || code.length() > 20) {
            return false;
        }
        // 排除纯数字且长度小于4的（可能是时间、日期的一部分）
        if (code.matches("\\d+") && code.length() < 4) {
            return false;
        }
        // 排除时间格式
        if (code.matches("\\d{1,2}:\\d{2}.*")) {
            return false;
        }
        return true;
    }
}
