-- 插入激活码使用页面（无需登录）
INSERT INTO tools.portal_system (name, description, url, icon, color, category, status, health_check_url, docs, download_path, tech_stack, sort_order)
VALUES (
    '激活码使用页面',
    '激活码在线解析与验证工具，无需登录即可使用',
    'https://tools.marschat.online/activecode/index.html',
    'Promotion',
    '#e6a23c',
    'web',
    1,
    NULL,
    NULL,
    NULL,
    '激活码解析与验证（无需登录）',
    3
);

-- 查询验证
SELECT id, name, url, category, sort_order 
FROM tools.portal_system 
WHERE deleted = 0 
ORDER BY sort_order, id;
