package com.kb.ops.dto;

import lombok.Data;

import java.util.List;

/**
 * 运维知识导入请求
 * <p>
 * 支持从结构化数据（解析自 Excel/CSV/JSON）批量导入主机与服务信息。
 */
@Data
public class ImportRequest {

    /** 导入类型: HOST / SERVICE / KNOWLEDGE */
    private String type;

    /** 是否覆盖已存在的同名记录 */
    private boolean override;

    /** 待导入的数据行，每行为 字段名 -> 值 */
    private List<java.util.Map<String, String>> rows;
}
