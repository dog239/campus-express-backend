package com.campusexpress.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体类
 * 对应数据库表：orders
 */
@TableName("orders")
public class Order {
    
    /**
     * 订单ID，主键自增
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 发布者ID（发单用户）
     */
    @TableField("requester_id")
    private Long requesterId;

    /**
     * 接单者ID（可为空）
     */
    @TableField("receiver_id")
    private Long receiverId;

    /**
     * 快递ID列表，逗号分隔（如 "101,102,103"）
     */
    @TableField("package_ids")
    private String packageIds;

    /**
     * 驿站名称
     */
    @TableField("station_name")
    private String stationName;

    /**
     * 小费金额
     */
    @TableField("tip_amount")
    private BigDecimal tipAmount;

    /**
     * 订单状态：0=待接单，1=已接单，2=已完成，3=已取消
     */
    private Integer status;

    /**
     * 拍照存证URL
     */
    @TableField("photo_url")
    private String photoUrl;

    /**
     * 发布时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 完成时间
     */
    @TableField("complete_time")
    private LocalDateTime completeTime;

    // Getter and Setter methods

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(Long requesterId) {
        this.requesterId = requesterId;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(Long receiverId) {
        this.receiverId = receiverId;
    }

    public String getPackageIds() {
        return packageIds;
    }

    public void setPackageIds(String packageIds) {
        this.packageIds = packageIds;
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
