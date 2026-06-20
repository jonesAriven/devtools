package com.kb.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kb.auth.entity.JwtBlacklist;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface JwtBlacklistMapper extends BaseMapper<JwtBlacklist> {
}
