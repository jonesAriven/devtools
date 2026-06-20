package com.kb.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kb.auth.entity.RefreshToken;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {
}
