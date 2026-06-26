-- 测试数据：1个用户占位（kb-knowledge 不维护用户表，仅 space.folder/tag.doc 关联 user_id=1）
-- 使用 MERGE INTO 避免重复插入（多测试类共享 H2 内存数据库）

-- 1 个默认空间（user_id=1）
MERGE INTO `space` (user_id, name, type, description, status, deleted) KEY(user_id, name)
VALUES (1, '我的空间', 'private', '集成测试默认空间', 1, 0);

-- 1 个根目录（space_id=1, parent_id=0）
MERGE INTO `folder` (space_id, parent_id, name, sort_order, deleted) KEY(space_id, parent_id, name)
VALUES (1, 0, '默认文件夹', 0, 0);

-- 1 个标签（user_id=1）
MERGE INTO `tag` (user_id, name, color, deleted) KEY(user_id, name)
VALUES (1, '重要', '#ff0000', 0);
