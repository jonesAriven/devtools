package com.jones.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jones.kb.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
