package com.campusexpress.service;

import com.campusexpress.entity.Order;
import com.campusexpress.vo.AvailableOrderVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单服务接口
 * 定义订单相关的业务操作
 */
public interface OrderService {

    /**
     * 发布代取需求
     * @param requesterId 发布者ID
     * @param deliveryIds 快递ID列表
     * @param tipAmount 小费金额
     * @param dormBuilding 宿舍楼（可选）
     * @return 创建的订单
     */
    Order publish(Long requesterId, List<Long> deliveryIds, BigDecimal tipAmount, String dormBuilding);

    /**
     * 接单
     * @param orderId 订单ID
     * @param receiverId 接单者ID
     * @return 更新后的订单
     */
    Order accept(Long orderId, Long receiverId);

    /**
     * 完成订单
     * @param orderId 订单ID
     * @param role 角色（REQUESTER或HELPER）
     * @return 更新后的订单
     */
    Order complete(Long orderId, String role);

    /**
     * 取消订单
     * @param orderId 订单ID
     * @return 更新后的订单
     */
    Order cancel(Long orderId);

    /**
     * 查询用户发布的订单
     * @param requesterId 发布者ID
     * @return 订单列表
     */
    List<Order> listByRequester(Long requesterId);

    /**
     * 查询用户接单的订单
     * @param receiverId 接单者ID
     * @return 订单列表
     */
    List<Order> listByReceiver(Long receiverId);

    /**
     * 获取待接单订单列表
     * @return 待接单订单列表
     */
    List<AvailableOrderVO> getAvailableOrders();
}
