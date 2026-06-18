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
     * 提取每个快递的信息（取件码 + 驿站地址 + 到达时间）
     */
    private List<Map<String, String>> extractPackages(String text) {
        List<Map<String, String>> packages = new ArrayList<>();
        System.out.println("=== 开始提取包裹信息 ===");
        
        // 合并所有行（移除换行符），便于跨行匹配
        String mergedText = text.replaceAll("\n", "");
        System.out.println("=== 合并后文本: " + mergedText);
        
        // 1. 找出所有取件码及其位置（在合并后的文本中）
        List<int[]> codePositions = findAllCodePositions(mergedText);
        System.out.println("=== 找到 " + codePositions.size() + " 个取件码");
        
        if (codePositions.isEmpty()) {
            // 备选方法
            List<String> allCodes = extractAllCodes(mergedText);
            String defaultStation = extractStation(mergedText);
            for (String code : allCodes) {
                Map<String, String> pkg = new java.util.HashMap<>();
                pkg.put("code", code);
                pkg.put("station", defaultStation);
                pkg.put("arrivalDate", "");
                packages.add(pkg);
            }
            return packages;
        }
        
        // 时间匹配模式（支持「周X」格式）
        Pattern timePattern = Pattern.compile(
            "(\\d{1,2}月\\d{1,2}日\\s*(?:周[一二三四五六日])?\\s*\\d{1,2}[:：]\\d{2})",
            Pattern.CASE_INSENSITIVE
        );
        
        // 2. 对每个取件码，提取它后面的文本和前面的时间
        for (int i = 0; i < codePositions.size(); i++) {
            int codeStart = codePositions.get(i)[0];
            int codeEnd = codePositions.get(i)[1];
            String code = mergedText.substring(codeStart, codeEnd);
            
            // 提取该取件码前面的文本（找时间，取前200个字符）
            int beforeStart = Math.max(0, codeStart - 200);
            String beforeText = mergedText.substring(beforeStart, codeStart);
            
            // 找到最近的时间
            Matcher timeMatcher = timePattern.matcher(beforeText);
            String time = null;
            while (timeMatcher.find()) {
                time = timeMatcher.group(1).trim();
            }
            
            // 转换时间为标准格式
            String arrivalDate = null;
            if (time != null) {
                arrivalDate = convertTimeToDate(time);
            }
            
            // 提取该取件码后面的文本（限制长度为80字符）
            int maxContextLen = 80;
            int contextEnd = Math.min(codeEnd + maxContextLen, mergedText.length());
            
            // 如果下一个取件码在范围内，截取到下一个取件码之前
            if (i + 1 < codePositions.size()) {
                int nextCodeStart = codePositions.get(i + 1)[0];
                if (nextCodeStart < contextEnd) {
                    contextEnd = nextCodeStart;
                }
            }
            
            String context = mergedText.substring(codeEnd, contextEnd);
            
            // 清理上下文：移除无关内容
            context = cleanContext(context);
            
            System.out.println("=== 取件码: " + code + ", 上下文: " + context + ", 时间: " + time);
            
            // 3. 从上下文中提取地址
            String station = extractStationFromContext(context);
            
            Map<String, String> pkg = new java.util.HashMap<>();
            pkg.put("code", code);
            pkg.put("station", station != null ? station : "未知驿站");
            pkg.put("time", time != null ? time : "");
            pkg.put("arrivalDate", arrivalDate != null ? arrivalDate : "");
            packages.add(pkg);
            System.out.println("=== 提取包裹: code=" + code + ", station=" + station + ", time=" + time + ", arrivalDate=" + arrivalDate);
        }
        
        return packages;
    }

    /**
     * 转换时间格式：3月8日09:15 -> 2026-03-08
     * 支持格式：5月8日 周五 17:17、3月8日 周日 09:15、5月4日15:52
     */
    private String convertTimeToDate(String time) {
        try {
            // 替换全角冒号为半角
            time = time.replace("：", ":");
            // 去除「周X」部分
            time = time.replaceAll("周[一二三四五六日]", "").trim();
            
            // 匹配月日和时间
            Pattern pattern = Pattern.compile("(\\d{1,2})月(\\d{1,2})日\\s*(\\d{1,2}):(\\d{2})");
            Matcher matcher = pattern.matcher(time);
            if (matcher.find()) {
                int month = Integer.parseInt(matcher.group(1));
                int day = Integer.parseInt(matcher.group(2));
                // 使用当前年份
                int year = java.time.LocalDate.now().getYear();
                // 如果月份大于当前月份，可能是去年的
                if (month > java.time.LocalDate.now().getMonthValue()) {
                    year = year - 1;
                }
                return String.format("%d-%02d-%02d", year, month, day);
            }
        } catch (Exception e) {
            System.err.println("转换时间失败: " + time);
        }
        return null;
    }

    /**
     * 清理上下文：移除无关内容
     */
    private String cleanContext(String context) {
        // 移除「创建笔记提醒」及其后面的内容
        int idx = context.indexOf("创建笔记提醒");
        if (idx > 0) {
            context = context.substring(0, idx);
        }
        
        // 移除日期格式（如「5月4日15:52」）
        context = context.replaceAll("\\d{1,2}月\\d{1,2}日\\d{1,2}:\\d{2}", "");
        
        // 移除「【近邻宝】凭」等开头的下一个快递信息
        idx = context.indexOf("【近邻宝】凭");
        if (idx > 0) {
            context = context.substring(0, idx);
        }
        idx = context.indexOf("【驿小哥】");
        if (idx > 0) {
            context = context.substring(0, idx);
        }
        idx = context.indexOf("【兔喜生活】");
        if (idx > 0) {
            context = context.substring(0, idx);
        }
        idx = context.indexOf("【妈妈驿站】");
        if (idx > 0) {
            context = context.substring(0, idx);
        }
        
        return context.trim();
    }

    /**
     * 找出所有取件码及其位置 [start, end]
     */
    private List<int[]> findAllCodePositions(String text) {
        List<int[]> positions = new ArrayList<>();
        
        Pattern[] patterns = {
            Pattern.compile("取件码\\s*[:：]?\\s*([A-Z0-9\\-]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("取货码\\s*[:：]?\\s*([A-Z0-9\\-]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("凭[「【]?\\s*([A-Z0-9\\-]+)\\s*[」】]?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("可凭\\s*([A-Z0-9\\-]+)\\s*到店", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b([A-Z0-9]+(?:-[A-Z0-9]+)+)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(\\d{6,10})\\b")
        };
        
        Set<Integer> usedPositions = new java.util.HashSet<>();
        
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                int start = m.start(1);
                int end = m.end(1);
                // 避免重复匹配同一位置
                if (!usedPositions.contains(start)) {
                    String code = m.group(1).trim();
                    if (code.length() >= 4 && code.length() <= 20) {
                        positions.add(new int[]{start, end});
                        usedPositions.add(start);
                    }
                }
            }
        }
        
        // 按位置排序
        positions.sort((a, b) -> a[0] - b[0]);
        
        return positions;
    }

    /**
     * 从上下文中提取地址（上下文已移除换行符）
     */
    private String extractStationFromContext(String context) {
        // 1. 优先匹配「地址：」后面的内容（取最长匹配）
        Pattern addrPattern = Pattern.compile("地址\\s*[:：]?\\s*([^，,。！？]{3,50})", Pattern.CASE_INSENSITIVE);
        Matcher addrMatcher = addrPattern.matcher(context);
        String longestAddr = null;
        while (addrMatcher.find()) {
            String result = addrMatcher.group(1).trim();
            if (result.length() > 3 && (longestAddr == null || result.length() > longestAddr.length())) {
                longestAddr = result;
            }
        }
        if (longestAddr != null) {
            return longestAddr;
        }
        
        // 2. 匹配「到XXX」格式（取最长匹配，允许跨行）
        Pattern toPattern = Pattern.compile("到\\s*([^，,。！？]{2,50})", Pattern.CASE_INSENSITIVE);
        Matcher toMatcher = toPattern.matcher(context);
        String longestTo = null;
        while (toMatcher.find()) {
            String result = toMatcher.group(1).trim();
            if (result.length() > 2 && (longestTo == null || result.length() > longestTo.length())) {
                longestTo = result;
            }
        }
        if (longestTo != null) {
            return longestTo;
        }
        
        // 3. 匹配已知驿站关键词并提取后面的位置信息
        Pattern keywordWithPosPattern = Pattern.compile(
            "(交大南门\\d+号柜[A-Z0-9]+|交大南门外近邻宝房后\\d+号[A-Z0-9]+|交通大学南门近邻宝院内货架|" +
            "交大南门近邻宝|近邻宝院内|近邻宝|妈妈驿站|菜鸟驿站|兔喜生活)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher keywordMatcher = keywordWithPosPattern.matcher(context);
        if (keywordMatcher.find()) {
            return keywordMatcher.group(1).trim();
        }
        
        return null;
    }

    /**
     * 检查地址是否被截断
     */
    private boolean isTruncatedStation(String station) {
        if (station == null || station.length() < 3) {
            return false;
        }
        // 以这些词结尾可能是被截断的
        String lastChar = station.substring(station.length() - 1);
        return lastChar.equals("近") || lastChar.equals("外") || lastChar.equals("门") ||
               lastChar.equals("南") || lastChar.equals("北") || lastChar.equals("东") || lastChar.equals("西");
    }

    /**
     * 合并被截断的地址行
     */
    private String mergeStationLines(String currentStation, String nextLine) {
        if (nextLine == null || nextLine.isEmpty()) {
            return null;
        }
        
        // 提取下一行的位置信息（取到逗号前）
        Pattern posPattern = Pattern.compile("^([^，,。！？\\n]{1,30})");
        Matcher posMatcher = posPattern.matcher(nextLine);
        if (posMatcher.find()) {
            String nextPart = posMatcher.group(1).trim();
            // 检查是否是位置信息（包含关键词或位置编号）
            if (nextPart.contains("邻宝") || nextPart.contains("房后") || nextPart.contains("柜") ||
                nextPart.contains("店") || nextPart.contains("驿站") || nextPart.contains("货架") ||
                nextPart.matches(".*\\d+号.*") || nextPart.matches(".*[A-Z]\\d+.*")) {
                return currentStation + nextPart;
            }
        }
        
        return null;
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
                 result.contains("货架") || result.contains("房后") || result.contains("近邻宝") ||
                 result.matches(".*\\d+号.*") || result.matches(".*[A-Z]\\d+.*"))) {
                return result;
            }
        }
        
        // 3. 匹配「凭XXX到XXX」格式
        Pattern pingToPattern = Pattern.compile("凭\\s*[A-Z0-9\\-]+\\s*到\\s*([^，,。！？\\n]{3,50})", Pattern.CASE_INSENSITIVE);
        Matcher pingToMatcher = pingToPattern.matcher(line);
        if (pingToMatcher.find()) {
            String result = pingToMatcher.group(1).trim();
            if (result.length() > 2) {
                return result;
            }
        }
        
        // 4. 匹配已知驿站关键词并提取后面的位置信息
        Pattern keywordWithPosPattern = Pattern.compile(
            "(交大南门\\d+号柜[A-Z0-9]+|交大南门外近邻宝房后\\d+号[A-Z0-9]+|交通大学南门近邻宝院内货架|" +
            "交大南门近邻宝|近邻宝院内|近邻宝|妈妈驿站|菜鸟驿站|兔喜生活)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher keywordWithPosMatcher = keywordWithPosPattern.matcher(line);
        if (keywordWithPosMatcher.find()) {
            return keywordWithPosMatcher.group(1).trim();
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
        
        // 3. 匹配「凭XXX到XXX」格式（提取完整位置）
        Pattern pingToPattern = Pattern.compile("凭\\s*[A-Z0-9\\-]+\\s*到\\s*([^，,。！？\\n]{3,50})", Pattern.CASE_INSENSITIVE);
        Matcher pingToMatcher = pingToPattern.matcher(text);
        String lastPingTo = null;
        while (pingToMatcher.find()) {
            lastPingTo = pingToMatcher.group(1).trim();
        }
        if (lastPingTo != null && lastPingTo.length() > 3) {
            System.out.println("=== 匹配到凭...到: " + lastPingTo);
            return lastPingTo;
        }
        
        // 4. 匹配「到XXX」提取完整位置（包括柜号、位置编号等）
        Pattern toPattern = Pattern.compile("到\\s*([^，,。！？\\n]{2,50})", Pattern.CASE_INSENSITIVE);
        Matcher toMatcher = toPattern.matcher(text);
        String lastTo = null;
        while (toMatcher.find()) {
            String result = toMatcher.group(1).trim();
            // 优先选择包含位置信息的
            if (result.contains("柜") || result.contains("店") || result.contains("驿站") || 
                result.contains("货架") || result.contains("房后") || result.contains("近邻宝") ||
                result.matches(".*\\d+号.*") || result.matches(".*[A-Z]\\d+.*")) {
                lastTo = result;
            }
        }
        if (lastTo != null && lastTo.length() > 2) {
            System.out.println("=== 匹配到到: " + lastTo);
            return lastTo;
        }
        
        // 5. 匹配已知驿站关键词并提取后面的位置信息
        Pattern keywordWithPosPattern = Pattern.compile(
            "(交大南门\\d+号柜[A-Z0-9]+|交大南门外近邻宝房后\\d+号[A-Z0-9]+|交通大学南门近邻宝院内货架|" +
            "交大南门近邻宝|近邻宝院内|近邻宝|妈妈驿站|菜鸟驿站|兔喜生活|顺丰驿站|丰巢快递柜|丰巢|京东驿站)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher keywordWithPosMatcher = keywordWithPosPattern.matcher(text);
        String lastKeyword = null;
        while (keywordWithPosMatcher.find()) {
            lastKeyword = keywordWithPosMatcher.group(1).trim();
        }
        if (lastKeyword != null) {
            System.out.println("=== 匹配到已知驿站: " + lastKeyword);
            return lastKeyword;
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