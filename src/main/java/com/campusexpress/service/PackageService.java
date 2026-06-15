package com.campusexpress.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campusexpress.entity.ExpressPackage;
import com.campusexpress.mapper.ExpressPackageMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PackageService {

    private static final List<String> SUPPORTED_STATIONS = Arrays.asList(
            "妈妈驿站",
            "东区篮球场",
            "近邻宝",
            "南门院内",
            "南门丰巢快递柜"
    );

    private final ExpressPackageMapper expressPackageMapper;

    public PackageService(ExpressPackageMapper expressPackageMapper) {
        this.expressPackageMapper = expressPackageMapper;
    }

    public void addPackage(Long userId, String pickupCode, String stationName, LocalDate arrivalDate) {
        if (userId == null) {
            throw new IllegalArgumentException("用户信息无效");
        }
        if (pickupCode == null || pickupCode.trim().isEmpty()) {
            throw new IllegalArgumentException("pickupCode 不能为空");
        }
        if (stationName == null || stationName.trim().isEmpty()) {
            throw new IllegalArgumentException("stationName 不能为空");
        }
        if (arrivalDate == null) {
            throw new IllegalArgumentException("arrivalDate 不能为空");
        }

        ExpressPackage expressPackage = new ExpressPackage();
        expressPackage.setUserId(userId);
        expressPackage.setPickupCode(pickupCode.trim());
        expressPackage.setStationName(stationName.trim());
        expressPackage.setArrivalDate(arrivalDate);
        expressPackage.setStatus(0);
        expressPackage.setDeleted(0);
        expressPackage.setCreateTime(LocalDateTime.now());
        expressPackage.setUpdateTime(LocalDateTime.now());

        expressPackageMapper.insert(expressPackage);
    }

    public Map<String, List<Map<String, Object>>> listMyPackages(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户信息无效");
        }

        List<ExpressPackage> packageList = expressPackageMapper.selectList(
                new QueryWrapper<ExpressPackage>()
                        .eq("user_id", userId)
                        .orderByAsc("arrival_date")
                        .orderByDesc("create_time")
        );

        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();

        for (ExpressPackage expressPackage : packageList) {
            String stationName = expressPackage.getStationName();
            if (!grouped.containsKey(stationName)) {
                grouped.put(stationName, new ArrayList<>());
            }
            grouped.get(stationName).add(toItem(expressPackage));
        }

        return grouped;
    }

    public List<String> getSupportedStations() {
        return SUPPORTED_STATIONS;
    }

    public void deletePackage(Long packageId, Long userId) {
        ExpressPackage expressPackage = expressPackageMapper.selectById(packageId);
        if (expressPackage == null) {
            throw new IllegalArgumentException("包裹不存在");
        }
        
        if (!expressPackage.getUserId().equals(userId)) {
            throw new IllegalArgumentException("只有包裹所有者可以删除");
        }
        
        expressPackageMapper.deleteById(packageId);
    }

    private Map<String, Object> toItem(ExpressPackage expressPackage) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", expressPackage.getId());
        item.put("pickupCode", expressPackage.getPickupCode());
        item.put("stationName", expressPackage.getStationName());
        item.put("arrivalDate", expressPackage.getArrivalDate());
        item.put("status", expressPackage.getStatus());
        item.put("createTime", expressPackage.getCreateTime());
        return item;
    }
}
