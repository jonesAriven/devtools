package com.frp.manager.config;

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
        // 审计列必须每次更新都刷新：strictUpdateFill 仅在字段为 null 时填充，
        // 而 update 常走「selectById → 改 → updateById」（实体带旧 updatedAt，非 null）
        // → 填充被跳过 → 旧值写回 SET → 压掉 MySQL 的 ON UPDATE CURRENT_TIMESTAMP → updated_at 冻结。
        this.setFieldValByName("updatedAt", LocalDateTime.now(), metaObject);
    }
}
