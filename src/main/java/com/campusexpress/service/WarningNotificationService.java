package com.campusexpress.service;

import com.campusexpress.config.WechatProperties;
import com.campusexpress.config.WarningProperties;
import com.campusexpress.entity.ExpressPackage;
import com.campusexpress.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class WarningNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WarningNotificationService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WarningProperties warningProperties;
    private final WechatProperties wechatProperties;
    private final WechatAccessTokenService wechatAccessTokenService;
    private final RestTemplate restTemplate = new RestTemplate();

    public WarningNotificationService(WarningProperties warningProperties,
                                      WechatProperties wechatProperties,
                                      WechatAccessTokenService wechatAccessTokenService) {
        this.warningProperties = warningProperties;
        this.wechatProperties = wechatProperties;
        this.wechatAccessTokenService = wechatAccessTokenService;
    }

    public void pushPackageWarning(User user, ExpressPackage expressPackage, String message) {
        if (warningProperties.getWechat().isEnabled()) {
            sendWechatSubscribeMessage(user, expressPackage, message);
            return;
        }

        if (!StringUtils.hasText(warningProperties.getWebhookUrl())) {
            log.warn("滞留预警通知（未配置 webhook，已记录日志） userId={}, packageId={}, openid={}, message={}",
                    user.getId(), expressPackage.getId(), user.getOpenid(), message);
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", user.getId());
        payload.put("openid", user.getOpenid());
        payload.put("packageId", expressPackage.getId());
        payload.put("pickupCode", expressPackage.getPickupCode());
        payload.put("stationName", expressPackage.getStationName());
        payload.put("arrivalDate", expressPackage.getArrivalDate());
        payload.put("message", message);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                warningProperties.getWebhookUrl(),
                request,
                String.class
        );
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("预警推送失败，HTTP 状态码: " + response.getStatusCodeValue());
        }
    }

    private void sendWechatSubscribeMessage(User user, ExpressPackage expressPackage, String message) {
        WarningProperties.Wechat wechat = warningProperties.getWechat();
        if (!StringUtils.hasText(user.getOpenid())) {
            throw new IllegalStateException("微信推送失败：用户 openid 为空");
        }
        if (!StringUtils.hasText(wechat.getTemplateId())) {
            throw new IllegalStateException("微信推送已启用，但 warning.wechat.template-id 未配置");
        }

        String accessToken = wechatAccessTokenService.getAccessToken();
        String url = String.format(wechatProperties.getSubscribeSendUrl(), accessToken);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("touser", user.getOpenid());
        payload.put("template_id", wechat.getTemplateId());
        payload.put("page", wechat.getPage());
        payload.put("miniprogram_state", wechat.getMiniprogramState());
        payload.put("lang", wechat.getLang());
        payload.put("data", buildWechatData(wechat, expressPackage, message));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
        if (response == null) {
            throw new IllegalStateException("微信推送失败：返回为空");
        }
        Object errCode = response.get("errcode");
        if (errCode != null && Integer.parseInt(String.valueOf(errCode)) != 0) {
            throw new IllegalStateException("微信推送失败: " + response.get("errmsg"));
        }
    }

    private Map<String, Object> buildWechatData(WarningProperties.Wechat wechat, ExpressPackage expressPackage, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        putWechatValue(data, wechat.getTitleField(), trimValue("滞留取件预警", 20));
        putWechatValue(data, wechat.getStationField(),
                trimValue(expressPackage.getStationName() + " 取件码:" + expressPackage.getPickupCode(), 20));
        putWechatValue(data, wechat.getCodeField(), trimValue(expressPackage.getPickupCode(), 32));
        LocalDateTime arrivalDateTime = expressPackage.getArrivalDate() != null
                ? expressPackage.getArrivalDate().atStartOfDay()
                : expressPackage.getCreateTime() != null ? expressPackage.getCreateTime()
                : expressPackage.getUpdateTime() != null ? expressPackage.getUpdateTime()
                : LocalDateTime.now();
        putWechatValue(data, wechat.getDateField(), DATE_TIME_FORMATTER.format(arrivalDateTime));
        putWechatValue(data, wechat.getRemarkField(), trimValue(message, 20));
        return data;
    }

    private void putWechatValue(Map<String, Object> data, String fieldName, String value) {
        if (!StringUtils.hasText(fieldName) || !StringUtils.hasText(value)) {
            return;
        }
        Map<String, String> item = new LinkedHashMap<>();
        item.put("value", value);
        data.put(fieldName, item);
    }

    private String trimValue(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }
}
