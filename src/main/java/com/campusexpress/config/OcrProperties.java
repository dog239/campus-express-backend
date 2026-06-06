package com.campusexpress.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "baidu.ocr")
public class OcrProperties {

    private String apiKey;
    private String secretKey;
    private String tokenUrl = "https://aip.baidubce.com/oauth/2.0/token";
    private String generalUrl = "https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getTokenUrl() {
        return tokenUrl;
    }

    public void setTokenUrl(String tokenUrl) {
        this.tokenUrl = tokenUrl;
    }

    public String getGeneralUrl() {
        return generalUrl;
    }

    public void setGeneralUrl(String generalUrl) {
        this.generalUrl = generalUrl;
    }
}
