package com.kb.ops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kb.ops.entity.OpsKnowledge;
import org.apache.ibatis.annotations.Update;

public interface OpsKnowledgeMapper extends BaseMapper<OpsKnowledge> {

    @Update("UPDATE ops_knowledge SET view_count = view_count + 1 WHERE id = #{id}")
    int incrementViewCount(Long id);
}
