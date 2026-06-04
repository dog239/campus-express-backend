package com.campusexpress.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wechat")
public class WechatProperties {

    private String appid;
    private String secret;
    private String sessionUrl = "https://api.weixin.qq.com/sns/jscode2session";
    private boolean debug = false;
}
