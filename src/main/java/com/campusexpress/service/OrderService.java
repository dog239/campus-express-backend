package com.campusexpress.service;

import com.campusexpress.entity.Order;
import com.campusexpress.vo.AvailableOrderVO;
import com.campusexpress.vo.OrderDetailVO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
     * 接单并返回取件码
     * @param orderId 订单ID
     * @param receiverId 接单者ID
     * @return 包含订单信息和取件码的Map
     */
    Map<String, Object> acceptWithPickupCodes(Long orderId, Long receiverId);

    /**
     * 确认完成
     * @param orderId 订单ID
     * @param userId 用户ID
     * @param role 角色（requester或receiver）
     * @return 更新后的订单
     */
    Order confirm(Long orderId, Long userId, String role);

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

    /**
     * 获取订单详情
     * @param orderId 订单ID
     * @return 订单详情
     */
    OrderDetailVO getOrderDetail(Long orderId);

    /**
     * 删除订单
     * @param orderId 订单ID
     * @param userId 用户ID
     */
    void deleteOrder(Long orderId, Long userId);

    /**
     * 更新订单凭证照片
     * @param orderId 订单ID
     * @param userId 用户ID
     * @param photoUrl 照片URL
     */
    void updatePhoto(Long orderId, Long userId, String photoUrl);
}
