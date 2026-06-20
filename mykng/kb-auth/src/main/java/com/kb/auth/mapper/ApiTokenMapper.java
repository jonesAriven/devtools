package com.kb.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kb.auth.entity.ApiToken;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApiTokenMapper extends BaseMapper<ApiToken> {
}
