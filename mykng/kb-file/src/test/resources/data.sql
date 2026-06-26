-- 测试数据：默认 Bucket
-- 使用 MERGE 避免重复插入（多测试类共享 H2 内存数据库）
MERGE INTO `bucket` (name, type, lifecycle_days) KEY(name) VALUES ('kb-file', 'file', NULL);
