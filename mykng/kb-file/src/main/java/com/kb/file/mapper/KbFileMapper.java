package com.kb.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kb.file.entity.KbFile;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface KbFileMapper extends BaseMapper<KbFile> {

    /**
     * 查询回收站文件列表（绕过 @TableLogic）
     */
    @Select("SELECT * FROM `file` WHERE user_id = #{userId} AND deleted = 1 ORDER BY updated_at DESC")
    List<KbFile> selectTrashList(@Param("userId") Long userId);

    /**
     * 根据 ID 查询已删除的文件（绕过 @TableLogic，用于恢复/永久删除前的校验）
     */
    @Select("SELECT * FROM `file` WHERE id = #{id} AND deleted = 1")
    KbFile selectDeletedById(@Param("id") Long id);

    /**
     * 恢复文件（绕过 @TableLogic，直接更新 deleted 字段）
     */
    @Update("UPDATE `file` SET deleted = 0 WHERE id = #{id}")
    int restoreById(@Param("id") Long id);

    /**
     * 物理删除单个文件（真正从数据库删除，绕过 @TableLogic）
     */
    @Delete("DELETE FROM `file` WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

    /**
     * 物理删除用户所有已删除文件（清空回收站）
     */
    @Delete("DELETE FROM `file` WHERE user_id = #{userId} AND deleted = 1")
    int physicalDeleteAllByUserId(@Param("userId") Long userId);
}
