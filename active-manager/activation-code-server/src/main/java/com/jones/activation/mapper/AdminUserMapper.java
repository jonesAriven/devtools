package com.jones.activation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jones.activation.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {
}
