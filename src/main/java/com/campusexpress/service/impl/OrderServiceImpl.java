package com.campusexpress.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campusexpress.entity.ExpressPackage;
import com.campusexpress.entity.Order;
import com.campusexpress.mapper.ExpressPackageMapper;
import com.campusexpress.mapper.OrderMapper;
import com.campusexpress.service.OrderService;
import com.campusexpress.vo.AvailableOrderVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单服务实现类
 */
@Service
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final ExpressPackageMapper expressPackageMapper;

    public OrderServiceImpl(OrderMapper orderMapper, ExpressPackageMapper expressPackageMapper) {
        this.orderMapper = orderMapper;
        this.expressPackageMapper = expressPackageMapper;
    }

    /**
     * 发布代取需求
     * @param requesterId 发布者ID
     * @param deliveryIds 快递ID列表
     * @param tipAmount 小费金额
     * @param dormBuilding 宿舍楼（可选）
     * @return 创建的订单
     */
    @Override
    public Order publish(Long requesterId, List<Long> deliveryIds, BigDecimal tipAmount, String dormBuilding) {
        // 参数验证
        if (requesterId == null) {
            throw new IllegalArgumentException("用户信息无效");
        }
        if (deliveryIds == null || deliveryIds.isEmpty()) {
            throw new IllegalArgumentException("deliveryIds 不能为空");
        }
        if (tipAmount == null || tipAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("小费金额无效");
        }

        // 查询第一个快递的驿站名称
        String stationName = null;
        ExpressPackage firstPackage = expressPackageMapper.selectById(deliveryIds.get(0));
        if (firstPackage != null) {
            stationName = firstPackage.getStationName();
        }

        // 创建订单
        Order order = new Order();
        order.setRequesterId(requesterId);
        order.setPackageIds(deliveryIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        order.setStationName(stationName != null ? stationName : (dormBuilding != null ? dormBuilding : ""));
        order.setTipAmount(tipAmount);
        order.setStatus(0); // 待接单
        order.setCreateTime(LocalDateTime.now());

        orderMapper.insert(order);
        return order;
    }

    /**
     * 接单
     * @param orderId 订单ID
     * @param receiverId 接单者ID
     * @return 更新后的订单
     */
    @Override
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

        order.setReceiverId(receiverId);
        order.setStatus(1);
        orderMapper.updateById(order);
        return order;
    }

    /**
     * 完成订单
     * @param orderId 订单ID
     * @param role 角色（REQUESTER或HELPER）
     * @return 更新后的订单
     */
    @Override
    public Order complete(Long orderId, String role) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!order.getStatus().equals(1)) {
            throw new IllegalArgumentException("订单状态不允许完成");
        }
        if (role == null || (!role.equals("REQUESTER") && !role.equals("HELPER"))) {
            throw new IllegalArgumentException("role 参数错误");
        }

        order.setStatus(2);
        order.setCompleteTime(LocalDateTime.now());
        orderMapper.updateById(order);
        return order;
    }

    /**
     * 取消订单
     * @param orderId 订单ID
     * @return 更新后的订单
     */
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

    /**
     * 查询用户发布的订单
     * @param requesterId 发布者ID
     * @return 订单列表
     */
    @Override
    public List<Order> listByRequester(Long requesterId) {
        return orderMapper.selectList(new QueryWrapper<Order>()
                .eq("requester_id", requesterId)
                .orderByDesc("create_time"));
    }

    /**
     * 查询用户接单的订单
     * @param receiverId 接单者ID
     * @return 订单列表
     */
    @Override
    public List<Order> listByReceiver(Long receiverId) {
        return orderMapper.selectList(new QueryWrapper<Order>()
                .eq("receiver_id", receiverId)
                .orderByDesc("create_time"));
    }

    /**
     * 获取待接单订单列表
     * @return 待接单订单列表
     */
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
}
