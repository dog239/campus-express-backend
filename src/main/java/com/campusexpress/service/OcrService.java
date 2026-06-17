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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OcrService {

    private final OcrProperties ocrProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    private String accessToken;
    private long accessTokenExpireAt;

    public OcrService(OcrProperties ocrProperties) {
        this.ocrProperties = ocrProperties;
    }

    /**
     * 提取所有取件码和驿站信息
     */
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
        
        String fullText = String.join("\n", textLines);
        System.out.println("=== OCR 识别全文: " + fullText);
        
        // 提取每个快递的信息（取件码 + 驿站地址）
        List<Map<String, String>> packages = extractPackages(fullText);
        System.out.println("=== 提取到包裹列表: " + packages);
        
        // 兼容旧版：提取所有取件码
        List<String> codes = new ArrayList<>();
        for (Map<String, String> pkg : packages) {
            if (pkg.containsKey("code")) {
                codes.add(pkg.get("code"));
            }
        }
        
        // 兼容旧版：提取第一个驿站
        String station = packages.isEmpty() ? "未知驿站" : packages.get(0).getOrDefault("station", "未知驿站");
        
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("packages", packages);
        result.put("codes", codes);
        result.put("station", station);
        result.put("code", codes.isEmpty() ? null : codes.get(0));
        
        System.out.println("=== 最终结果: " + result);
        return result;
    }

    /**
     * 提取每个快递的信息（取件码 + 驿站地址）
     */
    private List<Map<String, String>> extractPackages(String text) {
        List<Map<String, String>> packages = new ArrayList<>();
        System.out.println("=== 开始提取包裹信息 ===");
        
        // 按行分割文本
        String[] lines = text.split("\n");
        
        // 临时存储当前包裹信息
        String currentCode = null;
        String currentStation = null;
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            
            System.out.println("=== 处理行: " + line);
            
            // 检查是否是取件码行
            String code = extractCodeFromLine(line);
            if (code != null) {
                // 如果已有取件码，先保存
                if (currentCode != null) {
                    Map<String, String> pkg = new java.util.HashMap<>();
                    pkg.put("code", currentCode);
                    pkg.put("station", currentStation != null ? currentStation : "未知驿站");
                    packages.add(pkg);
                    System.out.println("=== 保存包裹: code=" + currentCode + ", station=" + currentStation);
                }
                
                currentCode = code;
                currentStation = null;
                
                // 从当前行提取地址
                currentStation = extractStationFromLine(line);
                
                // 如果当前行没有地址，向后查找
                if (currentStation == null || currentStation.equals("未知驿站")) {
                    for (int j = i + 1; j < lines.length && j < i + 5; j++) {
                        String nextLine = lines[j].trim();
                        String station = extractStationFromLine(nextLine);
                        if (station != null && !station.equals("未知驿站")) {
                            currentStation = station;
                            break;
                        }
                        // 如果遇到下一个取件码，停止查找
                        if (extractCodeFromLine(nextLine) != null) {
                            break;
                        }
                    }
                }
            } else {
                // 不是取件码行，检查是否是地址行
                String station = extractStationFromLine(line);
                if (station != null && !station.equals("未知驿站")) {
                    if (currentCode != null && currentStation == null) {
                        currentStation = station;
                    }
                }
            }
        }
        
        // 保存最后一个包裹
        if (currentCode != null) {
            Map<String, String> pkg = new java.util.HashMap<>();
            pkg.put("code", currentCode);
            pkg.put("station", currentStation != null ? currentStation : "未知驿站");
            packages.add(pkg);
            System.out.println("=== 保存最后一个包裹: code=" + currentCode + ", station=" + currentStation);
        }
        
        // 如果没有提取到，使用备选方法
        if (packages.isEmpty()) {
            System.out.println("=== 使用备选方法提取 ===");
            List<String> allCodes = extractAllCodes(text);
            String defaultStation = extractStation(text);
            
            for (String code : allCodes) {
                Map<String, String> pkg = new java.util.HashMap<>();
                pkg.put("code", code);
                pkg.put("station", defaultStation);
                packages.add(pkg);
            }
        }
        
        return packages;
    }

    /**
     * 从单行提取取件码
     */
    private String extractCodeFromLine(String line) {
        Pattern[] patterns = {
            Pattern.compile("取件码\\s*[:：]?\\s*([A-Z0-9\\-]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("取货码\\s*[:：]?\\s*([A-Z0-9\\-]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("凭[「【]?\\s*([A-Z0-9\\-]+)\\s*[」】]?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("可凭\\s*([A-Z0-9\\-]+)\\s*到店", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b([A-Z0-9]+(?:-[A-Z0-9]+)+)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(\\d{6,10})\\b")
        };
        
        for (Pattern p : patterns) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                String code = m.group(1).trim();
                if (code.length() >= 4 && code.length() <= 20) {
                    return code;
                }
            }
        }
        return null;
    }

    /**
     * 从单行提取驿站地址
     */
    private String extractStationFromLine(String line) {
        // 1. 优先匹配「地址：」后面的内容
        Pattern addrPattern = Pattern.compile("地址\\s*[:：]?\\s*([^，,。！？\\n]{3,50})", Pattern.CASE_INSENSITIVE);
        Matcher addrMatcher = addrPattern.matcher(line);
        if (addrMatcher.find()) {
            String result = addrMatcher.group(1).trim();
            if (result.length() > 3) {
                return result;
            }
        }
        
        // 2. 匹配「到XXX柜/店/驿站/货架/房后」
        Pattern toPattern = Pattern.compile("到\\s*([^，,。！？\\n]{2,40})", Pattern.CASE_INSENSITIVE);
        Matcher toMatcher = toPattern.matcher(line);
        if (toMatcher.find()) {
            String result = toMatcher.group(1).trim();
            if (result.length() > 2 && 
                (result.contains("柜") || result.contains("店") || result.contains("驿站") || 
                 result.contains("货架") || result.contains("房后") || result.contains("近邻宝"))) {
                return result;
            }
        }
        
        // 3. 匹配已知驿站关键词
        String[][] stationKeywords = {
            {"交通大学南门近邻宝院内货架", "交通大学南门近邻宝院内货架"},
            {"交大南门外近邻宝房后", "交大南门外近邻宝房后"},
            {"交大南门近邻宝", "交大南门近邻宝"},
            {"近邻宝院内", "近邻宝院内"},
            {"近邻宝", "近邻宝"},
            {"妈妈驿站", "妈妈驿站"},
            {"菜鸟驿站", "菜鸟驿站"},
            {"兔喜生活", "兔喜生活"}
        };
        for (String[] pair : stationKeywords) {
            if (line.contains(pair[0])) {
                return pair[1];
            }
        }
        
        return null;
    }

    /**
     * 提取所有取件码
     */
    private List<String> extractAllCodes(String text) {
        Set<String> codes = new LinkedHashSet<>();
        System.out.println("=== 开始提取所有取件码 ===");
        
        // 模式1：取件码关键词后的内容（支持多种格式）
        Pattern[] keywordPatterns = {
            Pattern.compile("取件码\\s*[:：]?\\s*([A-Z0-9\\-]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("取货码\\s*[:：]?\\s*([A-Z0-9\\-]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("凭[「【]?\\s*([A-Z0-9\\-]+)\\s*[」】]?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("可凭\\s*([A-Z0-9\\-]+)\\s*到店", Pattern.CASE_INSENSITIVE)
        };
        
        for (Pattern p : keywordPatterns) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                String code = m.group(1).trim();
                if (code.length() >= 4 && code.length() <= 20) {
                    codes.add(code);
                    System.out.println("=== 关键词匹配到取件码: " + code);
                }
            }
        }
        
        // 模式2：连字符格式（M-4-6993、7-1-1914）
        Pattern hyphenPattern = Pattern.compile("\\b([A-Z0-9]+(?:-[A-Z0-9]+)+)\\b", Pattern.CASE_INSENSITIVE);
        Matcher hyphenMatcher = hyphenPattern.matcher(text);
        while (hyphenMatcher.find()) {
            String code = hyphenMatcher.group(1).trim();
            if (code.length() >= 4 && code.length() <= 20 && !codes.contains(code)) {
                codes.add(code);
                System.out.println("=== 连字符格式匹配到取件码: " + code);
            }
        }
        
        // 模式3：纯数字格式（87491264、25701148）
        Pattern numPattern = Pattern.compile("\\b(\\d{6,10})\\b");
        Matcher numMatcher = numPattern.matcher(text);
        while (numMatcher.find()) {
            String code = numMatcher.group(1).trim();
            if (!codes.contains(code)) {
                codes.add(code);
                System.out.println("=== 纯数字格式匹配到取件码: " + code);
            }
        }
        
        // 模式4：备选（4-8位数字或字母数字组合）
        if (codes.isEmpty()) {
            Pattern fallbackPattern = Pattern.compile("\\b([A-Z]?[0-9\\-]{4,12})\\b");
            Matcher fallbackMatcher = fallbackPattern.matcher(text);
            while (fallbackMatcher.find()) {
                String code = fallbackMatcher.group(1).trim();
                if (code.length() >= 4 && !codes.contains(code)) {
                    codes.add(code);
                    System.out.println("=== 备选匹配到取件码: " + code);
                }
            }
        }
        
        return new ArrayList<>(codes);
    }

    /**
     * 提取驿站名称（优先完整地址）
     */
    private String extractStation(String text) {
        System.out.println("=== 开始提取驿站 ===");
        
        // 1. 优先提取「地址：」后面的完整地址（匹配最后一个，通常更完整）
        Pattern addrPattern = Pattern.compile("地址\\s*[:：]?\\s*([^，,。！？\\n]{3,50})", Pattern.CASE_INSENSITIVE);
        Matcher addrMatcher = addrPattern.matcher(text);
        String lastAddr = null;
        while (addrMatcher.find()) {
            lastAddr = addrMatcher.group(1).trim();
        }
        if (lastAddr != null && lastAddr.length() > 3) {
            System.out.println("=== 匹配到地址: " + lastAddr);
            return lastAddr;
        }
        
        // 2. 提取「取件地址：」后面的内容
        Pattern pickupAddrPattern = Pattern.compile("取件地址\\s*[:：]?\\s*([^，,。！？\\n]{3,50})", Pattern.CASE_INSENSITIVE);
        Matcher pickupAddrMatcher = pickupAddrPattern.matcher(text);
        if (pickupAddrMatcher.find()) {
            String result = pickupAddrMatcher.group(1).trim();
            if (result.length() > 3) {
                System.out.println("=== 匹配到取件地址: " + result);
                return result;
            }
        }
        
        // 3. 匹配「到XXX柜」或「到XXX号柜」
        Pattern cabinetPattern = Pattern.compile("到\\s*([^，,。！？\\n]{3,40})", Pattern.CASE_INSENSITIVE);
        Matcher cabinetMatcher = cabinetPattern.matcher(text);
        String lastCabinet = null;
        while (cabinetMatcher.find()) {
            String result = cabinetMatcher.group(1).trim();
            if (result.contains("柜") || result.contains("店") || result.contains("驿站") || 
                result.contains("货架") || result.contains("房后") || result.contains("近邻宝")) {
                lastCabinet = result;
            }
        }
        if (lastCabinet != null && lastCabinet.length() > 3) {
            System.out.println("=== 匹配到柜号: " + lastCabinet);
            return lastCabinet;
        }
        
        // 4. 匹配「到XXX」或「已到XXX」
        Pattern arrivePattern = Pattern.compile("(?:到|已到|到达|已送达)\\s*([^，,。！？\\n]{3,30})", Pattern.CASE_INSENSITIVE);
        Matcher arriveMatcher = arrivePattern.matcher(text);
        if (arriveMatcher.find()) {
            String result = arriveMatcher.group(1).trim();
            if (result.length() > 3) {
                System.out.println("=== 匹配到到: " + result);
                return result;
            }
        }
        
        // 5. 匹配已知驿站关键词（按优先级排序，优先匹配更完整的）
        String[][] stationKeywords = {
            {"交通大学南门近邻宝院内货架", "交通大学南门近邻宝院内货架"},
            {"交通大学南门近邻宝", "交通大学南门近邻宝"},
            {"近邻宝院内", "近邻宝院内"},
            {"近邻宝", "近邻宝"},
            {"妈妈驿站", "妈妈驿站"},
            {"菜鸟驿站", "菜鸟驿站"},
            {"兔喜生活", "兔喜生活"},
            {"顺丰驿站", "顺丰驿站"},
            {"顺丰代签收", "顺丰代签收"},
            {"丰巢快递柜", "丰巢快递柜"},
            {"丰巢", "丰巢"},
            {"京东驿站", "京东驿站"},
            {"京东服务站", "京东服务站"},
            {"比比吃旁菜鸟驿站", "比比吃旁菜鸟驿站"},
            {"比比吃", "比比吃"}
        };
        for (String[] pair : stationKeywords) {
            if (text.contains(pair[0])) {
                System.out.println("=== 匹配到已知驿站: " + pair[1]);
                return pair[1];
            }
        }
        
        // 6. 提取发送方名称
        Pattern senderPattern = Pattern.compile("【([^】]+)】");
        Matcher senderMatcher = senderPattern.matcher(text);
        if (senderMatcher.find()) {
            String sender = senderMatcher.group(1).trim();
            System.out.println("=== 匹配到发送方: " + sender);
            return sender;
        }
        
        System.out.println("=== 未匹配到驿站，返回未知驿站");
        return "未知驿站";
    }

    // ========== 以下为原有方法（保持不变） ==========

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

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new IllegalStateException("URL 编码失败", e);
        }
    }

    // CODE_PATTERN 保留用于兼容
    private static final Pattern CODE_PATTERN = Pattern.compile(
            "\\b(?:\\d{1,2}-\\d{1,2}-\\d{3,5}|\\d{1,2}-\\d{3,5}|[A-Z]?\\d{4,8}|[A-Z]\\d{4,8})\\b",
            Pattern.CASE_INSENSITIVE
    );
}