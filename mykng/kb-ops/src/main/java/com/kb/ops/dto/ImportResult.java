package com.kb.ops.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 导入结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportResult {

    private int total;

    private int success;

    private int failed;

    private int skipped;

    private java.util.List<String> errors;
}
