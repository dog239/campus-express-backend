package com.campusexpress.controller;

import com.campusexpress.common.Result;
import com.campusexpress.entity.Order;
import com.campusexpress.entity.User;
import com.campusexpress.service.OrderService;
import com.campusexpress.service.UserService;
import com.campusexpress.util.JwtUtil;
import com.campusexpress.vo.AvailableOrderVO;
import com.campusexpress.vo.OrderDetailVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @PostMapping("/publish")
    @Operation(summary = "Publish a pickup order", description = "User publishes a new express pickup order")
    public Result<Map<String, Object>> publish(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> request) {
        try {
            User currentUser = getCurrentUser(authorization);
            
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
            
            Order order = orderService.publish(currentUser.getId(), deliveryIds, tipAmount, dormBuilding);
            
            Map<String, Object> result = new HashMap<>();
            result.put("orderId", order.getId());
            return Result.success(result);
        } catch (Exception ex) {
            return Result.error(ex.getMessage());
        }
    }

    @PostMapping("/accept/{orderId}")
    @Operation(summary = "Accept an order", description = "User accepts a pickup order with optimistic lock")
    public Result<Map<String, Object>> accept(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long orderId) {
        try {
            User currentUser = getCurrentUser(authorization);
            Map<String, Object> result = orderService.acceptWithPickupCodes(orderId, currentUser.getId());
            return Result.success(result);
        } catch (Exception ex) {
            return Result.error(ex.getMessage());
        }
    }

    @PostMapping("/confirm/{orderId}")
    @Operation(summary = "Confirm order completion", description = "Requester or receiver confirms order completion")
    public Result<Map<String, Object>> confirm(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long orderId,
            @RequestBody Map<String, String> request) {
        try {
            User currentUser = getCurrentUser(authorization);
            String role = request.get("role");
            Order order = orderService.confirm(orderId, currentUser.getId(), role);
            Map<String, Object> result = new HashMap<>();
            result.put("orderId", order.getId());
            result.put("status", order.getStatus());
            result.put("requesterConfirm", order.getRequesterConfirm());
            result.put("receiverConfirm", order.getReceiverConfirm());
            return Result.success(result);
        } catch (Exception ex) {
            return Result.error(ex.getMessage());
        }
    }

    @GetMapping("/detail/{orderId}")
    @Operation(summary = "Get order detail", description = "Get detailed information of an order")
    public Result<OrderDetailVO> getDetail(@PathVariable Long orderId) {
        try {
            OrderDetailVO orderDetail = orderService.getOrderDetail(orderId);
            return Result.success(orderDetail);
        } catch (Exception ex) {
            return Result.error(ex.getMessage());
        }
    }

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

    @GetMapping("/my")
    @Operation(summary = "Get my orders", description = "Get orders published or accepted by current user")
    public Result<Map<String, Object>> getMyOrders(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            User currentUser = getCurrentUser(authorization);
            List<Order> publishedOrders = orderService.listByRequester(currentUser.getId());
            List<Order> acceptedOrders = orderService.listByReceiver(currentUser.getId());
            
            Map<String, Object> result = new HashMap<>();
            result.put("published", publishedOrders);
            result.put("accepted", acceptedOrders);
            return Result.success(result);
        } catch (Exception ex) {
            return Result.error(ex.getMessage());
        }
    }

    @PostMapping("/cancel/{orderId}")
    @Operation(summary = "Cancel an order", description = "Cancel an order by requester")
    public Result<Map<String, Object>> cancel(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long orderId) {
        try {
            User currentUser = getCurrentUser(authorization);
            Order order = orderService.cancel(orderId);
            Map<String, Object> result = new HashMap<>();
            result.put("orderId", order.getId());
            result.put("status", order.getStatus());
            return Result.success(result);
        } catch (Exception ex) {
            return Result.error(ex.getMessage());
        }
    }

    @DeleteMapping("/{orderId}")
    @Operation(summary = "Delete an order", description = "Delete an order")
    public Result<String> deleteOrder(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long orderId) {
        try {
            if (orderId == null || orderId <= 0) {
                return Result.error("订单ID无效");
            }
            User currentUser = getCurrentUser(authorization);
            orderService.deleteOrder(orderId, currentUser.getId());
            return Result.success("删除成功");
        } catch (Exception ex) {
            return Result.error(ex.getMessage());
        }
    }

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
