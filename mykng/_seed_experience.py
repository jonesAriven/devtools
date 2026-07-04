#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
快速演示：把这次部署踩过的坑记录到经验库
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from deploy_engine import ExperienceLogger, Color

# 初始化经验记录器
exp_config = {
    'enabled': True,
    'history_dir': '.deploy-history',
    'log_file': 'deploy-log.jsonl',
    'issues_file': 'known-issues.md',
    'patterns_dir': 'patterns',
}
project_root = str(Path(__file__).parent)
exp = ExperienceLogger(exp_config, project_root)

Color.banner("记录本次部署经验到知识库")

# 问题1：docker compose up -d 导致基础设施重启
exp.add_issue(
    title="Docker Compose 全量部署导致基础设施被重启",
    symptom="执行 docker compose up -d 后，mysql/redis 等基础设施被重新创建，服务连接数据库失败 (UnknownHostException)",
    cause="服务器上已有一套运行中的基础设施，但 compose 配置里也定义了这些服务，导致冲突。depends_on 级联重启让问题更严重。",
    solution="""
1. 部署前先 docker ps 勘察环境，确认哪些服务在跑
2. 代码变更用 docker-hotfix 热更新策略（docker cp + restart）
3. docker-compose.yml 把基础设施和应用服务拆分成两个文件
4. 优先使用增量部署，不要轻易全量重启
    """.strip(),
    service="通用"
)

# 问题2：Swagger 路径不匹配
exp.add_issue(
    title="网关 Swagger UI 聚合页 404 / API Docs 路由不匹配",
    symptom="访问 /kb/swagger-ui.html 返回 404，各服务的 /kb/api/{service}/v3/api-docs 也返回 404 或 503",
    cause="""
- Swagger UI 在网关根路径 /swagger-ui.html，而不是 /kb/swagger-ui.html
- API Docs 的 urls 配置缺少 /kb 前缀，网关路由不到
- kb-knowledge 的路径是 doc/folder/search 等，没有 /knowledge 前缀，所以 /kb/api/knowledge/v3/api-docs 不匹配业务路由
- StripPrefix=2 去掉了 /kb/api 两段，但 v3/api-docs 需要 StripPrefix=3 才能到达服务的根路径
    """.strip(),
    solution="""
1. 为每个服务添加专用的 Swagger API Docs 路由，放在业务路由前面
2. API Docs 路由使用 StripPrefix=3（去掉 /kb/api/{service} 三段）
3. springdoc.swagger-ui.urls 里的路径带 /kb 前缀
4. 路由顺序：API Docs 路由在前，业务路由在后
    """.strip(),
    service="kb-gateway"
)

# 问题3：EventBus Bean 缺失
exp.add_issue(
    title="kb-file 启动失败：No qualifying bean of type EventBus",
    symptom="kb-file 启动时反复重启，日志显示 APPLICATION FAILED TO START，原因是 EventBus Bean 找不到",
    cause="KbEventAutoConfig 中的 @ConditionalOnBean(StringRedisTemplate.class) 条件在某些启动顺序下不满足，导致 EventBus Bean 没有被注册。",
    solution="移除 KbEventAutoConfig 中 eventBus 方法上的 @ConditionalOnBean 注解，只保留 @ConditionalOnClass 和 @ConditionalOnMissingBean。只要 classpath 里有 StringRedisTemplate（即引入了 redis starter），就创建 EventBus Bean。",
    service="kb-file"
)

# 问题4：SSH 脚本超时
exp.add_issue(
    title="Python paramiko 脚本超时导致后台运行",
    symptom="部署脚本执行到一半被推到后台，看不到输出，不知道进度",
    cause="paramiko 的 exec_command 默认超时时间短，加上部署操作耗时较长（上传+重启+等待启动），容易超时。",
    solution="""
1. 用 nohup 在远程后台执行长任务，输出写到日志文件
2. 本地轮询读取日志文件增量，实时显示进度
3. 大任务拆成小步骤，每步都有超时控制
4. 使用 SSHManager 统一管理连接，避免反复建连
    """.strip(),
    service="通用"
)

Color.ok("所有经验已记录到 .deploy-history/known-issues.md")
print("\n下次部署前可以运行: python deploy_engine.py issues 查看已知问题库")
