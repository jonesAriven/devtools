package com.jones.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jones.kb.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
