package com.campusexpress.controller;

import com.campusexpress.common.Result;
import com.campusexpress.entity.Order;
import com.campusexpress.entity.User;
import com.campusexpress.service.OrderService;
import com.campusexpress.service.UserService;
import com.campusexpress.util.JwtUtil;
import com.campusexpress.vo.AvailableOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单控制器
 * 处理订单相关的HTTP请求
 */
@RestController
@RequestMapping("/api/order")
@Tag(name = "Order Management", description = "Order APIs")
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    public OrderController(OrderService orderService, UserService userService, JwtUtil jwtUtil) {
        this.orderService = orderService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 发布代取需求
     * @param authorization JWT token
     * @param request 请求参数 { deliveryIds: [Long], tipAmount: BigDecimal, dormBuilding: String }
     * @return 创建的订单ID
     */
    @PostMapping("/publish")
    @Operation(summary = "Publish a pickup order", description = "User publishes a new express pickup order")
    public Result<Map<String, Object>> publish(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> request
    ) {
        try {
            // 获取当前用户
            User currentUser = getCurrentUser(authorization);
            
            // 解析请求参数
            @SuppressWarnings("unchecked")
            List<Integer> deliveryIdInts = (List<Integer>) request.get("deliveryIds");
            if (deliveryIdInts == null || deliveryIdInts.isEmpty()) {
                return Result.error("deliveryIds 不能为空");
            }
            List<Long> deliveryIds = deliveryIdInts.stream()
                    .map(Integer::longValue)
                    .collect(Collectors.toList());
            
            Object tipAmountObj = request.get("tipAmount");
            if (tipAmountObj == null) {
                return Result.error("tipAmount 不能为空");
            }
            BigDecimal tipAmount = new BigDecimal(tipAmountObj.toString());
            
            String dormBuilding = (String) request.get("dormBuilding");
            
            // 发布订单
            Order order = orderService.publish(currentUser.getId(), deliveryIds, tipAmount, dormBuilding);
            
            return Result.success(Map.of("orderId", order.getId()));
        } catch (Exception ex) {
            return Result.error(ex.getMessage());
        }
    }

    /**
     * 获取待接单订单列表
     * @return 待接单订单列表
     */
    @GetMapping("/available")
    @Operation(summary = "Get available orders", description = "Query all orders with waiting status")
    public Result<List<AvailableOrderVO>> getAvailableOrders() {
        try {
            List<AvailableOrderVO> orders = orderService.getAvailableOrders();
            return Result.success(orders);
        } catch (Exception ex) {
            return Result.error(ex.getMessage());
        }
    }

    /**
     * 从请求头获取当前用户
     * @param authorization JWT token
     * @return 当前用户
     */
    private User getCurrentUser(String authorization) {
        if (authorization == null || authorization.isEmpty()) {
            throw new IllegalArgumentException("缺少 token");
        }
        String openid = jwtUtil.parseToken(authorization);
        User user = userService.getUserByOpenid(openid);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return user;
    }
}
