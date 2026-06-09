package com.campusexpress.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "warning")
public class WarningProperties {

    private String webhookUrl;
    private String scanCron = "0 0 2 * * ?";
    private final Wechat wechat = new Wechat();

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getScanCron() {
        return scanCron;
    }

    public void setScanCron(String scanCron) {
        this.scanCron = scanCron;
    }

    public Wechat getWechat() {
        return wechat;
    }

    public static class Wechat {
        private boolean enabled = false;
        private String templateId;
        private String page = "pages/package/list/index";
        private String miniprogramState = "formal";
        private String lang = "zh_CN";
        private String titleField = "thing1";
        private String stationField = "thing2";
        private String codeField = "character_string3";
        private String dateField = "time4";
        private String remarkField = "thing5";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTemplateId() {
            return templateId;
        }

        public void setTemplateId(String templateId) {
            this.templateId = templateId;
        }

        public String getPage() {
            return page;
        }

        public void setPage(String page) {
            this.page = page;
        }

        public String getMiniprogramState() {
            return miniprogramState;
        }

        public void setMiniprogramState(String miniprogramState) {
            this.miniprogramState = miniprogramState;
        }

        public String getLang() {
            return lang;
        }

        public void setLang(String lang) {
            this.lang = lang;
        }

        public String getTitleField() {
            return titleField;
        }

        public void setTitleField(String titleField) {
            this.titleField = titleField;
        }

        public String getStationField() {
            return stationField;
        }

        public void setStationField(String stationField) {
            this.stationField = stationField;
        }

        public String getCodeField() {
            return codeField;
        }

        public void setCodeField(String codeField) {
            this.codeField = codeField;
        }

        public String getDateField() {
            return dateField;
        }

        public void setDateField(String dateField) {
            this.dateField = dateField;
        }

        public String getRemarkField() {
            return remarkField;
        }

        public void setRemarkField(String remarkField) {
            this.remarkField = remarkField;
        }
    }
}
