package com.campusexpress.service;

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

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class WarningNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WarningNotificationService.class);

    private final WarningProperties warningProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    public WarningNotificationService(WarningProperties warningProperties) {
        this.warningProperties = warningProperties;
    }

    public void pushPackageWarning(User user, ExpressPackage expressPackage, String message) {
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
}
