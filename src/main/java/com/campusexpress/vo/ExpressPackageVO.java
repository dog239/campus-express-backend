package com.campusexpress.vo;

import java.time.LocalDate;

/**
 * 快递信息 VO
 */
public class ExpressPackageVO {
    private Long id;
    private String pickupCode;
    private String stationName;
    private LocalDate arrivalDate;

    public ExpressPackageVO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPickupCode() {
        return pickupCode;
    }

    public void setPickupCode(String pickupCode) {
        this.pickupCode = pickupCode;
    }

    public String getStationName() {
        return stationName;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }

    public LocalDate getArrivalDate() {
        return arrivalDate;
    }

    public void setArrivalDate(LocalDate arrivalDate) {
        this.arrivalDate = arrivalDate;
    }
}
