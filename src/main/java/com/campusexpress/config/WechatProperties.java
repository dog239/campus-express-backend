package com.campusexpress.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "wechat")
public class WechatProperties {

    private String appid;
    private String secret;
    private String sessionUrl = "https://api.weixin.qq.com/sns/jscode2session";
    private String accessTokenUrl = "https://api.weixin.qq.com/cgi-bin/token";
    private String subscribeSendUrl = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=%s";
    private boolean debug = false;

    public String getAppid() {
        return appid;
    }

    public void setAppid(String appid) {
        this.appid = appid;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getSessionUrl() {
        return sessionUrl;
    }

    public void setSessionUrl(String sessionUrl) {
        this.sessionUrl = sessionUrl;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public String getAccessTokenUrl() {
        return accessTokenUrl;
    }

    public void setAccessTokenUrl(String accessTokenUrl) {
        this.accessTokenUrl = accessTokenUrl;
    }

    public String getSubscribeSendUrl() {
        return subscribeSendUrl;
    }

    public void setSubscribeSendUrl(String subscribeSendUrl) {
        this.subscribeSendUrl = subscribeSendUrl;
    }
}
