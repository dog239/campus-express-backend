package com.campusexpress.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campusexpress.entity.ExpressPackage;
import com.campusexpress.entity.Order;
import com.campusexpress.entity.User;
import com.campusexpress.mapper.ExpressPackageMapper;
import com.campusexpress.mapper.OrderMapper;
import com.campusexpress.mapper.UserMapper;
import com.campusexpress.service.OrderService;
import com.campusexpress.vo.AvailableOrderVO;
import com.campusexpress.vo.ExpressPackageVO;
import com.campusexpress.vo.OrderDetailVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单服务实现类
 */
@Service
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final ExpressPackageMapper expressPackageMapper;
    private final UserMapper userMapper;

    public OrderServiceImpl(OrderMapper orderMapper, ExpressPackageMapper expressPackageMapper, UserMapper userMapper) {
        this.orderMapper = orderMapper;
        this.expressPackageMapper = expressPackageMapper;
        this.userMapper = userMapper;
    }

    @Override
    public Order publish(Long requesterId, List<Long> deliveryIds, BigDecimal tipAmount, String dormBuilding) {
        if (requesterId == null) {
            throw new IllegalArgumentException("用户信息无效");
        }
        if (deliveryIds == null || deliveryIds.isEmpty()) {
            throw new IllegalArgumentException("deliveryIds 不能为空");
        }
        if (tipAmount == null || tipAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("小费金额无效");
        }

        String stationName = null;
        ExpressPackage firstPackage = expressPackageMapper.selectById(deliveryIds.get(0));
        if (firstPackage != null) {
            stationName = firstPackage.getStationName();
        }

        Order order = new Order();
        order.setRequesterId(requesterId);
        order.setPackageIds(deliveryIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        order.setStationName(stationName != null ? stationName : (dormBuilding != null ? dormBuilding : ""));
        order.setTipAmount(tipAmount);
        order.setStatus(0);
        order.setRequesterConfirm(false);
        order.setReceiverConfirm(false);
        order.setCreateTime(LocalDateTime.now());
        order.setVersion(0);

        orderMapper.insert(order);
        return order;
    }

    @Override
    @Transactional
    public Order accept(Long orderId, Long receiverId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!order.getStatus().equals(0)) {
            throw new IllegalArgumentException("订单状态不允许接单");
        }
        if (order.getRequesterId().equals(receiverId)) {
            throw new IllegalArgumentException("不能接自己的订单");
        }

        Order updateOrder = new Order();
        updateOrder.setId(orderId);
        updateOrder.setReceiverId(receiverId);
        updateOrder.setStatus(1);
        updateOrder.setVersion(order.getVersion());

        int updated = orderMapper.updateById(updateOrder);
        if (updated == 0) {
            throw new IllegalArgumentException("订单已被其他人接单，请刷新页面重试");
        }

        return orderMapper.selectById(orderId);
    }

    @Override
    @Transactional
    public Order confirm(Long orderId, Long userId, String role) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!order.getStatus().equals(1)) {
            throw new IllegalArgumentException("订单状态不允许确认完成");
        }
        if (role == null || (!role.equals("requester") && !role.equals("receiver"))) {
            throw new IllegalArgumentException("role 参数错误");
        }

        if ("requester".equals(role)) {
            if (!order.getRequesterId().equals(userId)) {
                throw new IllegalArgumentException("只有发布者可以进行此操作");
            }
            order.setRequesterConfirm(true);
        } else {
            if (!order.getReceiverId().equals(userId)) {
                throw new IllegalArgumentException("只有接单者可以进行此操作");
            }
            order.setReceiverConfirm(true);
        }

        if (Boolean.TRUE.equals(order.getRequesterConfirm()) && Boolean.TRUE.equals(order.getReceiverConfirm())) {
            order.setStatus(2);
            order.setCompleteTime(LocalDateTime.now());
        }

        orderMapper.updateById(order);
        return order;
    }

    @Override
    public Order cancel(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!order.getStatus().equals(0)) {
            throw new IllegalArgumentException("订单状态不允许取消");
        }

        order.setStatus(3);
        orderMapper.updateById(order);
        return order;
    }

    @Override
    public List<Order> listByRequester(Long requesterId) {
        return orderMapper.selectList(new QueryWrapper<Order>()
                .eq("requester_id", requesterId)
                .orderByDesc("create_time"));
    }

    @Override
    public List<Order> listByReceiver(Long receiverId) {
        return orderMapper.selectList(new QueryWrapper<Order>()
                .eq("receiver_id", receiverId)
                .orderByDesc("create_time"));
    }

    @Override
    public List<AvailableOrderVO> getAvailableOrders() {
        List<Order> orders = orderMapper.selectList(new QueryWrapper<Order>()
                .eq("status", 0)
                .orderByDesc("create_time"));

        return orders.stream().map(order -> {
            AvailableOrderVO vo = new AvailableOrderVO();
            vo.setOrderId(order.getId());
            vo.setRequesterId(order.getRequesterId());
            vo.setStationName(order.getStationName());
            vo.setTipAmount(order.getTipAmount());
            vo.setStatus("WAITING");
            vo.setCreateTime(order.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public OrderDetailVO getOrderDetail(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }

        OrderDetailVO vo = new OrderDetailVO();
        vo.setOrderId(order.getId());
        vo.setRequesterId(order.getRequesterId());
        vo.setReceiverId(order.getReceiverId());
        vo.setStationName(order.getStationName());
        vo.setTipAmount(order.getTipAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusText(getStatusText(order.getStatus()));
        vo.setRequesterConfirm(order.getRequesterConfirm());
        vo.setReceiverConfirm(order.getReceiverConfirm());
        vo.setPhotoUrl(order.getPhotoUrl());
        vo.setCreateTime(order.getCreateTime());
        vo.setCompleteTime(order.getCompleteTime());

        if (order.getPackageIds() != null && !order.getPackageIds().isEmpty()) {
            List<Long> packageIdList = Arrays.stream(order.getPackageIds().split(","))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
            vo.setPackageIds(packageIdList);

            List<ExpressPackage> packages = expressPackageMapper.selectBatchIds(packageIdList);
            List<ExpressPackageVO> packageVOs = packages.stream().map(pkg -> {
                ExpressPackageVO packageVO = new ExpressPackageVO();
                packageVO.setId(pkg.getId());
                packageVO.setPickupCode(pkg.getPickupCode());
                packageVO.setStationName(pkg.getStationName());
                packageVO.setArrivalDate(pkg.getArrivalDate());
                return packageVO;
            }).collect(Collectors.toList());
            vo.setPackages(packageVOs);
        }

        User requester = userMapper.selectById(order.getRequesterId());
        if (requester != null) {
            vo.setRequesterNickname(requester.getNickname());
            vo.setRequesterAvatar(requester.getAvatar());
        }

        if (order.getReceiverId() != null) {
            User receiver = userMapper.selectById(order.getReceiverId());
            if (receiver != null) {
                vo.setReceiverNickname(receiver.getNickname());
                vo.setReceiverAvatar(receiver.getAvatar());
            }
        }

        return vo;
    }

    private String getStatusText(Integer status) {
        switch (status) {
            case 0:
                return "待接单";
            case 1:
                return "已接单";
            case 2:
                return "已完成";
            case 3:
                return "已取消";
            default:
                return "未知";
        }
    }
}
