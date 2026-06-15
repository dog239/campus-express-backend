package com.campusexpress.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusexpress.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT COUNT(*) FROM package WHERE user_id = #{userId}")
    Integer countPackagesByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM orders WHERE requester_id = #{userId} OR receiver_id = #{userId}")
    Integer countOrdersByUserId(@Param("userId") Long userId);

    @Select("SELECT IFNULL(SUM(tip_amount), 0) FROM orders WHERE receiver_id = #{userId} AND status = 2")
    String sumEarningsByUserId(@Param("userId") Long userId);
}
