-- ============================================================
-- mykng 知识库微服务 - V1 初始化 Schema 校验脚本 (SOP V1.1 要求)
-- ============================================================
-- 版本: V1 (verify)
-- 说明: 校验 V1__init_schema.sql 执行后的表结构完整性
-- 校验内容:
--   1. 所有表已创建（information_schema.TABLES）
--   2. 关键表的列存在（information_schema.COLUMNS）
--   3. 关键索引存在（information_schema.STATISTICS）
--   4. 字符集为 utf8mb4_unicode_ci
--   5. 输出校验报告
-- 使用: 直接在 MySQL 中执行，查看输出结果（PASS/FAIL）
-- ============================================================

-- ============================================================
-- 0. 校验报告头部
-- ============================================================
SELECT '============================================================' AS report;
SELECT '= mykng V1 Schema 校验报告' AS report;
SELECT '= 校验时间: ' AS report, NOW() AS check_time;
SELECT '= 数据库实例: ' AS report, @@hostname AS host, @@port AS port;
SELECT '============================================================' AS report;


-- ============================================================
-- 1. 数据库存在性校验（期望: 5 个数据库）
-- ============================================================
SELECT '========== 1. 数据库存在性校验 ==========' AS report;

SELECT
    CASE WHEN COUNT(*) = 5 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    CONCAT('数据库总数: ', COUNT(*), ' / 5') AS check_item
FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME IN ('kb_auth', 'kb_file', 'kb_knowledge', 'kb_ops', 'kb_intelligence');

SELECT
    CASE WHEN COUNT(*) > 0 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    CONCAT('数据库存在: ', SCHEMA_NAME) AS check_item,
    CONCAT('字符集: ', DEFAULT_CHARACTER_SET_NAME, ' / 排序规则: ', DEFAULT_COLLATION_NAME) AS detail
FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME IN ('kb_auth', 'kb_file', 'kb_knowledge', 'kb_ops', 'kb_intelligence')
ORDER BY SCHEMA_NAME;


-- ============================================================
-- 2. 表存在性校验（期望: 38 张表）
-- ============================================================
SELECT '========== 2. 表存在性校验（期望 38 张表） ==========' AS report;

-- 2.1 总表数校验
SELECT
    CASE WHEN COUNT(*) = 38 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    CONCAT('表总数: ', COUNT(*), ' / 38') AS check_item
FROM information_schema.TABLES
WHERE TABLE_SCHEMA IN ('kb_auth', 'kb_file', 'kb_knowledge', 'kb_ops', 'kb_intelligence')
  AND TABLE_TYPE = 'BASE TABLE';

-- 2.2 kb_auth 表校验（期望 4 张）
SELECT
    CASE WHEN COUNT(*) = 4 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    CONCAT('kb_auth 表数: ', COUNT(*), ' / 4') AS check_item
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'kb_auth' AND TABLE_TYPE = 'BASE TABLE';

SELECT
    CASE WHEN COUNT(*) > 0 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    CONCAT('kb_auth.', TABLE_NAME) AS check_item
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'kb_auth' AND TABLE_TYPE = 'BASE TABLE'
ORDER BY TABLE_NAME;

-- 2.3 kb_file 表校验（期望 3 张）
SELECT
    CASE WHEN COUNT(*) = 3 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    CONCAT('kb_file 表数: ', COUNT(*), ' / 3') AS check_item
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'kb_file' AND TABLE_TYPE = 'BASE TABLE';

SELECT
    CASE WHEN COUNT(*) > 0 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    CONCAT('kb_file.', TABLE_NAME) AS check_item
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'kb_file' AND TABLE_TYPE = 'BASE TABLE'
ORDER BY TABLE_NAME;

-- 2.4 kb_ops 表校验（期望 11 张）
SELECT
    CASE WHEN COUNT(*) = 11 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    CONCAT('kb_ops 表数: ', COUNT(*), ' / 11') AS check_item
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'kb_ops' AND TABLE_TYPE = 'BASE TABLE';

SELECT
    CASE WHEN COUNT(*) > 0 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    CONCAT('kb_ops.', TABLE_NAME) AS check_item
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'kb_ops' AND TABLE_TYPE = 'BASE TABLE'
ORDER BY TABLE_NAME;

-- 2.5 kb_knowledge 表校验（期望 9 张）
SELECT
    CASE WHEN COUNT(*) = 9 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    CONCAT('kb_knowledge 表数: ', COUNT(*), ' / 9') AS check_item
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'kb_knowledge' AND TABLE_TYPE = 'BASE TABLE';

SELECT
    CASE WHEN COUNT(*) > 0 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    CONCAT('kb_knowledge.', TABLE_NAME) AS check_item
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'kb_knowledge' AND TABLE_TYPE = 'BASE TABLE'
ORDER BY TABLE_NAME;

-- 2.6 kb_intelligence 表校验（期望 11 张）
SELECT
    CASE WHEN COUNT(*) = 11 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    CONCAT('kb_intelligence 表数: ', COUNT(*), ' / 11') AS check_item
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'kb_intelligence' AND TABLE_TYPE = 'BASE TABLE';

SELECT
    CASE WHEN COUNT(*) > 0 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    CONCAT('kb_intelligence.', TABLE_NAME) AS check_item
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'kb_intelligence' AND TABLE_TYPE = 'BASE TABLE'
ORDER BY TABLE_NAME;


-- ============================================================
-- 3. 关键列存在性校验（每库抽样核心表）
-- ============================================================
SELECT '========== 3. 关键列存在性校验 ==========' AS report;

-- 3.1 kb_auth.user 关键列
SELECT
    CASE WHEN COUNT(*) = 5 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    'kb_auth.user 关键列完整（id/username/password/status/deleted）' AS check_item
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'kb_auth' AND TABLE_NAME = 'user'
  AND COLUMN_NAME IN ('id', 'username', 'password', 'status', 'deleted');

-- 3.2 kb_auth.ops_api_token 关键列
SELECT
    CASE WHEN COUNT(*) = 4 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    'kb_auth.ops_api_token 关键列完整（user_id/token_encrypted/name/status）' AS check_item
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'kb_auth' AND TABLE_NAME = 'ops_api_token'
  AND COLUMN_NAME IN ('user_id', 'token_encrypted', 'name', 'status');

-- 3.3 kb_file.file 关键列
SELECT
    CASE WHEN COUNT(*) = 5 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    'kb_file.file 关键列完整（user_id/name/minio_path/parse_status/deleted）' AS check_item
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'kb_file' AND TABLE_NAME = 'file'
  AND COLUMN_NAME IN ('user_id', 'name', 'minio_path', 'parse_status', 'deleted');

-- 3.4 kb_ops.ops_host 关键列
SELECT
    CASE WHEN COUNT(*) = 4 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    'kb_ops.ops_host 关键列完整（name/ip/status/deleted）' AS check_item
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'kb_ops' AND TABLE_NAME = 'ops_host'
  AND COLUMN_NAME IN ('name', 'ip', 'status', 'deleted');

-- 3.5 kb_ops.ops_service 关键列
SELECT
    CASE WHEN COUNT(*) = 5 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    'kb_ops.ops_service 关键列完整（name/type/host_id/status/deleted）' AS check_item
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'kb_ops' AND TABLE_NAME = 'ops_service'
  AND COLUMN_NAME IN ('name', 'type', 'host_id', 'status', 'deleted');

-- 3.6 kb_knowledge.space 关键列
SELECT
    CASE WHEN COUNT(*) = 5 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    'kb_knowledge.space 关键列完整（user_id/name/type/status/deleted）' AS check_item
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'kb_knowledge' AND TABLE_NAME = 'space'
  AND COLUMN_NAME IN ('user_id', 'name', 'type', 'status', 'deleted');

-- 3.7 kb_knowledge.doc 关键列
SELECT
    CASE WHEN COUNT(*) = 4 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    'kb_knowledge.doc 关键列完整（folder_id/user_id/title/deleted）' AS check_item
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'kb_knowledge' AND TABLE_NAME = 'doc'
  AND COLUMN_NAME IN ('folder_id', 'user_id', 'title', 'deleted');

-- 3.8 kb_intelligence.kn_doc 关键列
SELECT
    CASE WHEN COUNT(*) = 5 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    'kb_intelligence.kn_doc 关键列完整（title/file_path/doc_type/status/deleted）' AS check_item
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'kb_intelligence' AND TABLE_NAME = 'kn_doc'
  AND COLUMN_NAME IN ('title', 'file_path', 'doc_type', 'status', 'deleted');

-- 3.9 kb_intelligence.kn_host 关键列
SELECT
    CASE WHEN COUNT(*) = 4 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    'kb_intelligence.kn_host 关键列完整（name/ip/status/deleted）' AS check_item
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'kb_intelligence' AND TABLE_NAME = 'kn_host'
  AND COLUMN_NAME IN ('name', 'ip', 'status', 'deleted');


-- ============================================================
-- 4. 关键索引存在性校验
-- ============================================================
SELECT '========== 4. 关键索引存在性校验 ==========' AS report;

-- 4.1 kb_auth.user 主键与唯一索引
SELECT
    CASE WHEN COUNT(*) > 0 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    'kb_auth.user 主键 idx 存在 (PRIMARY)' AS check_item
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'kb_auth' AND TABLE_NAME = 'user' AND INDEX_NAME = 'PRIMARY';

SELECT
    CASE WHEN COUNT(*) > 0 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    'kb_auth.user 唯一索引 uk_username 存在' AS check_item
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'kb_auth' AND TABLE_NAME = 'user' AND INDEX_NAME = 'uk_username';

-- 4.2 kb_file.bucket 唯一索引
SELECT
    CASE WHEN COUNT(*) > 0 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    'kb_file.bucket 唯一索引 uk_name 存在' AS check_item
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'kb_file' AND TABLE_NAME = 'bucket' AND INDEX_NAME = 'uk_name';

-- 4.3 kb_ops.ops_snapshot 唯一索引
SELECT
    CASE WHEN COUNT(*) > 0 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    'kb_ops.ops_snapshot 唯一索引 uk_date_key 存在' AS check_item
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'kb_ops' AND TABLE_NAME = 'ops_snapshot' AND INDEX_NAME = 'uk_date_key';

-- 4.4 kb_ops.ops_host 普通索引
SELECT
    CASE WHEN COUNT(*) > 0 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    'kb_ops.ops_host 索引 idx_ip 存在' AS check_item
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'kb_ops' AND TABLE_NAME = 'ops_host' AND INDEX_NAME = 'idx_ip';

-- 4.5 kb_ops.ops_service 普通索引
SELECT
    CASE WHEN COUNT(*) > 0 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    'kb_ops.ops_service 索引 idx_host_id 存在' AS check_item
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'kb_ops' AND TABLE_NAME = 'ops_service' AND INDEX_NAME = 'idx_host_id';

-- 4.6 kb_knowledge.share 唯一索引
SELECT
    CASE WHEN COUNT(*) > 0 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    'kb_knowledge.share 唯一索引 uk_code 存在' AS check_item
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'kb_knowledge' AND TABLE_NAME = 'share' AND INDEX_NAME = 'uk_code';

-- 4.7 kb_knowledge.folder 普通索引
SELECT
    CASE WHEN COUNT(*) > 0 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    'kb_knowledge.folder 索引 idx_space_id 存在' AS check_item
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'kb_knowledge' AND TABLE_NAME = 'folder' AND INDEX_NAME = 'idx_space_id';

-- 4.8 kb_intelligence.kn_host 唯一索引
SELECT
    CASE WHEN COUNT(*) > 0 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    'kb_intelligence.kn_host 唯一索引 uk_ip 存在' AS check_item
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'kb_intelligence' AND TABLE_NAME = 'kn_host' AND INDEX_NAME = 'uk_ip';

-- 4.9 kb_intelligence.kn_doc 普通索引
SELECT
    CASE WHEN COUNT(*) > 0 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    'kb_intelligence.kn_doc 索引 idx_doc_type 存在' AS check_item
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'kb_intelligence' AND TABLE_NAME = 'kn_doc' AND INDEX_NAME = 'idx_doc_type';


-- ============================================================
-- 5. 字符集校验（期望全部为 utf8mb4_unicode_ci）
-- ============================================================
SELECT '========== 5. 字符集校验（utf8mb4_unicode_ci） ==========' AS report;

-- 5.1 统计非 utf8mb4_unicode_ci 的表（期望 0 张）
SELECT
    CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    CONCAT('非 utf8mb4_unicode_ci 表数: ', COUNT(*), ' (期望 0)') AS check_item
FROM information_schema.TABLES
WHERE TABLE_SCHEMA IN ('kb_auth', 'kb_file', 'kb_knowledge', 'kb_ops', 'kb_intelligence')
  AND TABLE_TYPE = 'BASE TABLE'
  AND TABLE_COLLATION <> 'utf8mb4_unicode_ci';

-- 5.2 列出所有非 utf8mb4_unicode_ci 的表（如有）
SELECT
    'FAIL' AS check_result,
    CONCAT(TABLE_SCHEMA, '.', TABLE_NAME, ' 排序规则=', TABLE_COLLATION) AS check_item
FROM information_schema.TABLES
WHERE TABLE_SCHEMA IN ('kb_auth', 'kb_file', 'kb_knowledge', 'kb_ops', 'kb_intelligence')
  AND TABLE_TYPE = 'BASE TABLE'
  AND TABLE_COLLATION <> 'utf8mb4_unicode_ci'
ORDER BY TABLE_SCHEMA, TABLE_NAME;

-- 5.3 各库表字符集汇总
SELECT
    TABLE_SCHEMA AS db_name,
    TABLE_COLLATION AS collation,
    COUNT(*) AS table_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA IN ('kb_auth', 'kb_file', 'kb_knowledge', 'kb_ops', 'kb_intelligence')
  AND TABLE_TYPE = 'BASE TABLE'
GROUP BY TABLE_SCHEMA, TABLE_COLLATION
ORDER BY TABLE_SCHEMA;


-- ============================================================
-- 6. 种子数据校验（V1 内置的 admin 用户、默认 bucket）
-- ============================================================
SELECT '========== 6. 种子数据校验 ==========' AS report;

-- 6.1 admin 用户存在
SELECT
    CASE WHEN COUNT(*) > 0 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    CONCAT('kb_auth.user admin 用户存在: ', COUNT(*), ' 条') AS check_item
FROM `kb_auth`.`user`
WHERE username = 'admin' AND deleted = 0;

-- 6.2 默认 bucket 存在
SELECT
    CASE WHEN COUNT(*) > 0 THEN 'PASS' ELSE 'FAIL' END AS check_result,
    CONCAT('kb_file.bucket 默认 kb-file 存在: ', COUNT(*), ' 条') AS check_item
FROM `kb_file`.`bucket`
WHERE name = 'kb-file';


-- ============================================================
-- 7. 表行数统计（数据迁移后用于核对数据量）
-- ============================================================
SELECT '========== 7. 表行数统计 ==========' AS report;

SELECT 'kb_auth.user' AS table_name, COUNT(*) AS row_count FROM `kb_auth`.`user`
UNION ALL
SELECT 'kb_auth.refresh_token', COUNT(*) FROM `kb_auth`.`refresh_token`
UNION ALL
SELECT 'kb_auth.jwt_blacklist', COUNT(*) FROM `kb_auth`.`jwt_blacklist`
UNION ALL
SELECT 'kb_auth.ops_api_token', COUNT(*) FROM `kb_auth`.`ops_api_token`
UNION ALL
SELECT 'kb_file.file', COUNT(*) FROM `kb_file`.`file`
UNION ALL
SELECT 'kb_file.file_chunk', COUNT(*) FROM `kb_file`.`file_chunk`
UNION ALL
SELECT 'kb_file.bucket', COUNT(*) FROM `kb_file`.`bucket`
UNION ALL
SELECT 'kb_ops.ops_host', COUNT(*) FROM `kb_ops`.`ops_host`
UNION ALL
SELECT 'kb_ops.ops_service', COUNT(*) FROM `kb_ops`.`ops_service`
UNION ALL
SELECT 'kb_ops.ops_change_log', COUNT(*) FROM `kb_ops`.`ops_change_log`
UNION ALL
SELECT 'kb_ops.ops_knowledge', COUNT(*) FROM `kb_ops`.`ops_knowledge`
UNION ALL
SELECT 'kb_ops.ops_conflict', COUNT(*) FROM `kb_ops`.`ops_conflict`
UNION ALL
SELECT 'kb_ops.ops_snapshot', COUNT(*) FROM `kb_ops`.`ops_snapshot`
UNION ALL
SELECT 'kb_ops.operation_log', COUNT(*) FROM `kb_ops`.`operation_log`
UNION ALL
SELECT 'kb_ops.ops_port', COUNT(*) FROM `kb_ops`.`ops_port`
UNION ALL
SELECT 'kb_ops.ops_credential', COUNT(*) FROM `kb_ops`.`ops_credential`
UNION ALL
SELECT 'kb_ops.ops_domain', COUNT(*) FROM `kb_ops`.`ops_domain`
UNION ALL
SELECT 'kb_ops.ops_dependency', COUNT(*) FROM `kb_ops`.`ops_dependency`
UNION ALL
SELECT 'kb_knowledge.space', COUNT(*) FROM `kb_knowledge`.`space`
UNION ALL
SELECT 'kb_knowledge.folder', COUNT(*) FROM `kb_knowledge`.`folder`
UNION ALL
SELECT 'kb_knowledge.doc', COUNT(*) FROM `kb_knowledge`.`doc`
UNION ALL
SELECT 'kb_knowledge.web_page', COUNT(*) FROM `kb_knowledge`.`web_page`
UNION ALL
SELECT 'kb_knowledge.tag', COUNT(*) FROM `kb_knowledge`.`tag`
UNION ALL
SELECT 'kb_knowledge.resource_tag', COUNT(*) FROM `kb_knowledge`.`resource_tag`
UNION ALL
SELECT 'kb_knowledge.share', COUNT(*) FROM `kb_knowledge`.`share`
UNION ALL
SELECT 'kb_knowledge.share_access_log', COUNT(*) FROM `kb_knowledge`.`share_access_log`
UNION ALL
SELECT 'kb_knowledge.version', COUNT(*) FROM `kb_knowledge`.`version`
UNION ALL
SELECT 'kb_intelligence.kn_doc', COUNT(*) FROM `kb_intelligence`.`kn_doc`
UNION ALL
SELECT 'kb_intelligence.kn_host', COUNT(*) FROM `kb_intelligence`.`kn_host`
UNION ALL
SELECT 'kb_intelligence.kn_service', COUNT(*) FROM `kb_intelligence`.`kn_service`
UNION ALL
SELECT 'kb_intelligence.kn_port', COUNT(*) FROM `kb_intelligence`.`kn_port`
UNION ALL
SELECT 'kb_intelligence.kn_credential', COUNT(*) FROM `kb_intelligence`.`kn_credential`
UNION ALL
SELECT 'kb_intelligence.kn_domain', COUNT(*) FROM `kb_intelligence`.`kn_domain`
UNION ALL
SELECT 'kb_intelligence.kn_dependency', COUNT(*) FROM `kb_intelligence`.`kn_dependency`
UNION ALL
SELECT 'kb_intelligence.kn_command', COUNT(*) FROM `kb_intelligence`.`kn_command`
UNION ALL
SELECT 'kb_intelligence.kn_timeline', COUNT(*) FROM `kb_intelligence`.`kn_timeline`
UNION ALL
SELECT 'kb_intelligence.kn_doc_entity_ref', COUNT(*) FROM `kb_intelligence`.`kn_doc_entity_ref`;


-- ============================================================
-- 8. 校验报告汇总
-- ============================================================
SELECT '========== 8. 校验报告汇总 ==========' AS report;

SELECT
    CONCAT(
        '校验结论: ',
        CASE
            WHEN (SELECT COUNT(*) FROM information_schema.SCHEMATA
                  WHERE SCHEMA_NAME IN ('kb_auth','kb_file','kb_knowledge','kb_ops','kb_intelligence')) = 5
             AND (SELECT COUNT(*) FROM information_schema.TABLES
                  WHERE TABLE_SCHEMA IN ('kb_auth','kb_file','kb_knowledge','kb_ops','kb_intelligence')
                    AND TABLE_TYPE = 'BASE TABLE') = 38
             AND (SELECT COUNT(*) FROM information_schema.TABLES
                  WHERE TABLE_SCHEMA IN ('kb_auth','kb_file','kb_knowledge','kb_ops','kb_intelligence')
                    AND TABLE_TYPE = 'BASE TABLE'
                    AND TABLE_COLLATION <> 'utf8mb4_unicode_ci') = 0
            THEN '✅ 全部通过（5 库 / 38 表 / 字符集一致）'
            ELSE '❌ 存在失败项，请检查上方 FAIL 记录'
        END
    ) AS final_result;

SELECT '============================================================' AS report;
SELECT '= 校验结束' AS report;
SELECT '============================================================' AS report;
