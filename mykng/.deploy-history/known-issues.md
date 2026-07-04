# 已知问题库

> 自动记录部署中遇到的问题和解决方案


## 2026-07-04 - Docker Compose 全量部署导致基础设施被重启

**症状**: 执行 docker compose up -d 后，mysql/redis 等基础设施被重新创建，服务连接数据库失败 (UnknownHostException)

**服务**: 通用

**根因**: 服务器上已有一套运行中的基础设施，但 compose 配置里也定义了这些服务，导致冲突。depends_on 级联重启让问题更严重。

**解决方案**: 1. 部署前先 docker ps 勘察环境，确认哪些服务在跑
2. 代码变更用 docker-hotfix 热更新策略（docker cp + restart）
3. docker-compose.yml 把基础设施和应用服务拆分成两个文件
4. 优先使用增量部署，不要轻易全量重启

---

## 2026-07-04 - 网关 Swagger UI 聚合页 404 / API Docs 路由不匹配

**症状**: 访问 /kb/swagger-ui.html 返回 404，各服务的 /kb/api/{service}/v3/api-docs 也返回 404 或 503

**服务**: kb-gateway

**根因**: - Swagger UI 在网关根路径 /swagger-ui.html，而不是 /kb/swagger-ui.html
- API Docs 的 urls 配置缺少 /kb 前缀，网关路由不到
- kb-knowledge 的路径是 doc/folder/search 等，没有 /knowledge 前缀，所以 /kb/api/knowledge/v3/api-docs 不匹配业务路由
- StripPrefix=2 去掉了 /kb/api 两段，但 v3/api-docs 需要 StripPrefix=3 才能到达服务的根路径

**解决方案**: 1. 为每个服务添加专用的 Swagger API Docs 路由，放在业务路由前面
2. API Docs 路由使用 StripPrefix=3（去掉 /kb/api/{service} 三段）
3. springdoc.swagger-ui.urls 里的路径带 /kb 前缀
4. 路由顺序：API Docs 路由在前，业务路由在后

---

## 2026-07-04 - kb-file 启动失败：No qualifying bean of type EventBus

**症状**: kb-file 启动时反复重启，日志显示 APPLICATION FAILED TO START，原因是 EventBus Bean 找不到

**服务**: kb-file

**根因**: KbEventAutoConfig 中的 @ConditionalOnBean(StringRedisTemplate.class) 条件在某些启动顺序下不满足，导致 EventBus Bean 没有被注册。

**解决方案**: 移除 KbEventAutoConfig 中 eventBus 方法上的 @ConditionalOnBean 注解，只保留 @ConditionalOnClass 和 @ConditionalOnMissingBean。只要 classpath 里有 StringRedisTemplate（即引入了 redis starter），就创建 EventBus Bean。

---

## 2026-07-04 - Python paramiko 脚本超时导致后台运行

**症状**: 部署脚本执行到一半被推到后台，看不到输出，不知道进度

**服务**: 通用

**根因**: paramiko 的 exec_command 默认超时时间短，加上部署操作耗时较长（上传+重启+等待启动），容易超时。

**解决方案**: 1. 用 nohup 在远程后台执行长任务，输出写到日志文件
2. 本地轮询读取日志文件增量，实时显示进度
3. 大任务拆成小步骤，每步都有超时控制
4. 使用 SSHManager 统一管理连接，避免反复建连

---

## 2026-07-04 - 启动监控误判：历史日志匹配到旧的成功关键词

**症状**: 热更新后监控启动状态，几秒内就报告'启动成功'，但实际服务还在启动中。日志显示匹配到了上一次启动的 Started Kb 关键词。

**服务**: 通用

**根因**: 使用 docker logs -f 时默认输出全部历史日志，然后才开始 follow 新日志。历史日志中包含了上一次启动的成功关键词，导致误判。

**解决方案**: 使用 docker logs --tail 0 -f <container>，--tail 0 表示从第 0 行历史开始（即不输出任何历史日志），只 follow 新产生的日志。这是 docker 原生支持的参数，最可靠。不要用「等几秒再读」「按字节偏移量跳过历史」这类 hack 方案。

---
