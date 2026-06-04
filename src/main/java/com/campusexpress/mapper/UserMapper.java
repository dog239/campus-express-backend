package com.campusexpress.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusexpress.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
