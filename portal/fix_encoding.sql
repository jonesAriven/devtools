SET NAMES utf8mb4;
USE tools;

UPDATE portal_system 
SET name = '激活码使用页面',
    description = '激活码在线解析与验证工具，无需登录即可使用',
    tech_stack = '激活码解析与验证（无需登录）'
WHERE id = 18;

SELECT id, name, description, tech_stack FROM portal_system WHERE id = 18;
