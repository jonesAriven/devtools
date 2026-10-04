package com.kb.portal.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 审计列必须每次更新都刷新，故无条件赋值（不能用 strictUpdateFill）。
        // 背景：strictUpdateFill 仅在字段为 null 时填充；而 PortalSystemServiceImpl.update()
        // 走「selectById → copyFromRequest → updateById」，实体自带旧 updatedAt（非 null），
        // 填充被跳过 → 旧值被写回 SET → 恰好压掉 MySQL 列上的 ON UPDATE CURRENT_TIMESTAMP
        //（MySQL 规则：显式赋值为当前值时不触发自动更新）→ updated_at 永远停在旧值。
        this.setFieldValByName("updatedAt", LocalDateTime.now(), metaObject);
    }
}
