package com.campusexpress.service;

import com.campusexpress.config.WechatProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Map;

@Service
public class WechatAccessTokenService {

    private final WechatProperties wechatProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    private volatile String cachedAccessToken;
    private volatile Instant expireAt = Instant.EPOCH;

    public WechatAccessTokenService(WechatProperties wechatProperties) {
        this.wechatProperties = wechatProperties;
    }

    public String getAccessToken() {
        if (StringUtils.hasText(cachedAccessToken) && Instant.now().isBefore(expireAt)) {
            return cachedAccessToken;
        }

        synchronized (this) {
            if (StringUtils.hasText(cachedAccessToken) && Instant.now().isBefore(expireAt)) {
                return cachedAccessToken;
            }

            if (!StringUtils.hasText(wechatProperties.getAppid()) || !StringUtils.hasText(wechatProperties.getSecret())) {
                throw new IllegalStateException("微信推送已启用，但 wechat.appid / wechat.secret 未配置");
            }

            String url = UriComponentsBuilder.fromHttpUrl(wechatProperties.getAccessTokenUrl())
                    .queryParam("grant_type", "client_credential")
                    .queryParam("appid", wechatProperties.getAppid())
                    .queryParam("secret", wechatProperties.getSecret())
                    .toUriString();

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null) {
                throw new IllegalStateException("获取微信 access_token 失败：返回为空");
            }
            if (response.get("errcode") != null) {
                throw new IllegalStateException("获取微信 access_token 失败: " + response.get("errmsg"));
            }

            Object accessToken = response.get("access_token");
            Object expiresIn = response.get("expires_in");
            if (accessToken == null || expiresIn == null) {
                throw new IllegalStateException("获取微信 access_token 失败：返回缺少字段");
            }

            long expiresSeconds = Long.parseLong(String.valueOf(expiresIn));
            cachedAccessToken = String.valueOf(accessToken);
            expireAt = Instant.now().plusSeconds(Math.max(60, expiresSeconds - 300));
            return cachedAccessToken;
        }
    }
}
