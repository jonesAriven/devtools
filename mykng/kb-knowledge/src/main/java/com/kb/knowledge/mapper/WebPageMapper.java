package com.kb.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kb.knowledge.entity.WebPage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface WebPageMapper extends BaseMapper<WebPage> {

    /**
     * 查询回收站网页列表（绕过 @TableLogic）
     */
    @Select("SELECT * FROM web_page WHERE user_id = #{userId} AND deleted = 1 ORDER BY updated_at DESC")
    List<WebPage> selectTrashList(@Param("userId") Long userId);

    /**
     * 根据 ID 查询已删除的网页（绕过 @TableLogic，用于恢复操作）
     */
    @Select("SELECT * FROM web_page WHERE id = #{id} AND deleted = 1")
    WebPage selectDeletedById(@Param("id") Long id);

    /**
     * 恢复网页（绕过 @TableLogic，直接更新 deleted 字段）
     */
    @Update("UPDATE web_page SET deleted = 0 WHERE id = #{id}")
    int restoreById(@Param("id") Long id);

    /**
     * 物理删除网页（绕过 @TableLogic 的逻辑删除，真正从数据库删除）
     */
    @Delete("DELETE FROM web_page WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

    @Delete("DELETE FROM web_page WHERE user_id = #{userId} AND deleted = 1")
    int physicalDeleteAllByUserId(@Param("userId") Long userId);
}
