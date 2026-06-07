package com.campusexpress.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusexpress.entity.Order;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单Mapper接口
 * 继承MyBatis-Plus的BaseMapper，提供基础CRUD操作
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
