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
        System.out.println("=== extractPackageInfo 开始执行 ===");
        System.out.println("=== imageBase64 长度: " + (imageBase64 != null ? imageBase64.length() : 0));
        
        if (imageBase64 == null || imageBase64.trim().isEmpty()) {
            throw new IllegalArgumentException("请提供图片");
        }

        String normalizedImage = normalizeBase64(imageBase64);
        System.out.println("=== 规范化后 base64 长度: " + normalizedImage.length());

        System.out.println("=== 准备调用百度 OCR...");
        List<String> textLines = callBaiduOcr(normalizedImage);
        System.out.println("=== 百度 OCR 返回文字行数: " + (textLines != null ? textLines.size() : 0));
        
        String fullText = String.join("", textLines);
        System.out.println("=== OCR 识别全文: " + fullText);
        
        String code = extractCode(fullText);
        System.out.println("=== 提取取件码: " + code);
        
        String station = extractStation(fullText);
        System.out.println("=== 提取驿站: " + station);
        
        Map<String, Object> result = new HashMap<>();
        result.put("code", code);
        result.put("station", station);
        
        System.out.println("=== 最终结果: " + result);
        return result;
    }

    private String extractCode(String text) {
        System.out.println("=== 开始提取取件码，文本长度: " + text.length());
        
        Pattern[] patterns = {
            Pattern.compile("凭[「【]?\\s*([A-Z0-9]+-[A-Z0-9]+(?:-[A-Z0-9]+)?)\\s*[」】]?\\s*到", Pattern.CASE_INSENSITIVE),
            Pattern.compile("凭[「【]?\\s*(\\d{4,8})\\s*[」】]?\\s*到", Pattern.CASE_INSENSITIVE),
            Pattern.compile("取货码\\s*([A-Z0-9]+-[A-Z0-9]+(?:-[A-Z0-9]+)?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("取货码\\s*(\\d{4,8})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("取件码\\s*([A-Z0-9]+-[A-Z0-9]+(?:-[A-Z0-9]+)?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("取件码\\s*(\\d{4,8})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("可凭\\s*(\\d{4,8})\\s*到店", Pattern.CASE_INSENSITIVE),
            Pattern.compile("凭\\s*(\\d{4,8})\\s*到店", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(\\d{4,8})\\b")
        };
        
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                String result = m.group(1).trim();
                System.out.println("=== 匹配到取件码: " + result + " 使用模式: " + p.pattern());
                return result;
            }
        }
        
        System.out.println("=== 未匹配到任何取件码");
        return null;
    }
    
    private String extractStation(String text) {
        System.out.println("=== 开始提取驿站，文本长度: " + text.length());
        
        Pattern[] patterns = {
            Pattern.compile("(?:取件地址|地址)[:：]?\\s*([^，,。！？\\n]{2,40})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("到\\s*([^，,。！？\\n]{2,40}(?:号柜|柜|店|驿站))", Pattern.CASE_INSENSITIVE),
            Pattern.compile("到\\s*([^，,。！？\\n]{2,30})", Pattern.CASE_INSENSITIVE)
        };
        
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                String result = m.group(1).trim();
                if (result.length() > 2) {
                    System.out.println("=== 匹配到驿站: " + result);
                    return result;
                }
            }
        }
        
        String[] stations = {
            "妈妈驿站", "近邻宝院内", "近邻宝", "菜鸟驿站",
            "兔喜生活", "兔喜超市", "顺丰驿站", "顺丰代签收",
            "丰巢快递柜", "丰巢", "京东驿站", "京东服务站",
            "比比吃旁菜鸟驿站", "比比吃", "快递柜"
        };
        for (String s : stations) {
            if (text.contains(s)) {
                System.out.println("=== 匹配到已知驿站: " + s);
                return s;
            }
        }
        
        Matcher senderMatch = Pattern.compile("【([^】]+)】").matcher(text);
        if (senderMatch.find()) {
            System.out.println("=== 匹配到发送方: " + senderMatch.group(1));
            return senderMatch.group(1);
        }
        
        System.out.println("=== 未匹配到驿站");
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
        try {
            String token = getAccessToken();
            String url = ocrProperties.getGeneralUrl() + "?access_token=" + urlEncode(token);
            System.out.println("=== 调用百度 OCR URL: " + url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("image", imageBase64);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

            System.out.println("=== 百度 OCR 返回结果: " + response);

            if (response == null) {
                throw new IllegalStateException("百度 OCR 返回为空");
            }
            if (response.containsKey("error_code")) {
                System.err.println("=== 百度 OCR 错误: " + response.get("error_code") + " " + response.get("error_msg"));
                throw new IllegalStateException("百度 OCR 失败: " + response.get("error_code") + " " + response.get("error_msg"));
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

            System.out.println("=== OCR 识别到的文字行数: " + lines.size());
            return lines;
        } catch (Exception e) {
            System.err.println("=== 百度 OCR 调用异常: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("百度 OCR 调用失败: " + e.getMessage(), e);
        }
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

}
