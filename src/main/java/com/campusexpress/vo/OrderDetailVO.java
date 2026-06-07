package com.campusexpress.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情 VO
 */
public class OrderDetailVO {
    private Long orderId;
    private Long requesterId;
    private String requesterNickname;
    private String requesterAvatar;
    private Long receiverId;
    private String receiverNickname;
    private String receiverAvatar;
    private List<Long> packageIds;
    private List<ExpressPackageVO> packages;
    private String stationName;
    private BigDecimal tipAmount;
    private Integer status;
    private String statusText;
    private Boolean requesterConfirm;
    private Boolean receiverConfirm;
    private String photoUrl;
    private LocalDateTime createTime;
    private LocalDateTime completeTime;

    public OrderDetailVO() {
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(Long requesterId) {
        this.requesterId = requesterId;
    }

    public String getRequesterNickname() {
        return requesterNickname;
    }

    public void setRequesterNickname(String requesterNickname) {
        this.requesterNickname = requesterNickname;
    }

    public String getRequesterAvatar() {
        return requesterAvatar;
    }

    public void setRequesterAvatar(String requesterAvatar) {
        this.requesterAvatar = requesterAvatar;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(Long receiverId) {
        this.receiverId = receiverId;
    }

    public String getReceiverNickname() {
        return receiverNickname;
    }

    public void setReceiverNickname(String receiverNickname) {
        this.receiverNickname = receiverNickname;
    }

    public String getReceiverAvatar() {
        return receiverAvatar;
    }

    public void setReceiverAvatar(String receiverAvatar) {
        this.receiverAvatar = receiverAvatar;
    }

    public List<Long> getPackageIds() {
        return packageIds;
    }

    public void setPackageIds(List<Long> packageIds) {
        this.packageIds = packageIds;
    }

    public List<ExpressPackageVO> getPackages() {
        return packages;
    }

    public void setPackages(List<ExpressPackageVO> packages) {
        this.packages = packages;
    }

    public String getStationName() {
        return stationName;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }

    public BigDecimal getTipAmount() {
        return tipAmount;
    }

    public void setTipAmount(BigDecimal tipAmount) {
        this.tipAmount = tipAmount;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }

    public Boolean getRequesterConfirm() {
        return requesterConfirm;
    }

    public void setRequesterConfirm(Boolean requesterConfirm) {
        this.requesterConfirm = requesterConfirm;
    }

    public Boolean getReceiverConfirm() {
        return receiverConfirm;
    }

    public void setReceiverConfirm(Boolean receiverConfirm) {
        this.receiverConfirm = receiverConfirm;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getCompleteTime() {
        return completeTime;
    }

    public void setCompleteTime(LocalDateTime completeTime) {
        this.completeTime = completeTime;
    }
}
