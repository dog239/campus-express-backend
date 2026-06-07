package com.campusexpress.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 待接单订单视图对象
 * 用于返回给前端的订单列表数据
 */
public class AvailableOrderVO {
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 发布者ID
     */
    private Long requesterId;
    
    /**
     * 宿舍楼（配送地址）
     */
    private String dormBuilding;
    
    /**
     * 驿站名称
     */
    private String stationName;
    
    /**
     * 小费金额
     */
    private BigDecimal tipAmount;
    
    /**
     * 订单状态
     */
    private String status;
    
    /**
     * 发布时间
     */
    private LocalDateTime createTime;

    // Getter and Setter methods

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

    public String getDormBuilding() {
        return dormBuilding;
    }

    public void setDormBuilding(String dormBuilding) {
        this.dormBuilding = dormBuilding;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
