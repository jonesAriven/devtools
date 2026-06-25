package com.kb.ops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kb.ops.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
