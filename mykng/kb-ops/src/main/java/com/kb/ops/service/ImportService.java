package com.kb.ops.service;

import com.kb.ops.dto.ImportRequest;
import com.kb.ops.dto.ImportResult;

public interface ImportService {

    /**
     * 导入运维数据。
     * 支持类型: HOST / SERVICE / KNOWLEDGE
     * rows 为字段名 -> 值 的映射列表（可由 CSV/Excel/JSON 解析得到）。
     */
    ImportResult importData(ImportRequest request);
}
