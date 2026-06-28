# language: zh-CN
# SOP V1.1 阶段2.5 强制要求：BDD 场景文件
# 模块：kb-intelligence 知识导入
# 覆盖业务流程：知识导入-文档解析-实体提取-双维度渲染
Feature: 知识导入与文档智能解析
  作为一个运维人员
  我想要按路径批量导入文档并自动解析
  以便将分散的运维知识结构化为机器可读与人类可读的双维度数据

  # ============ Happy Path：按路径导入 ============

  Scenario: 按路径导入单个文档
    Given 本地路径 "/data/docs/host-config.md" 存在文档
    When 用户调用按路径导入接口，路径 "/data/docs/host-config.md"
    Then 文档导入成功，返回文档 ID
    And 文档内容写入 MongoDB
    And 文档索引写入 MeiliSearch
    And 文档元数据写入 kn_doc 表

  Scenario: 按路径批量导入目录
    Given 本地目录 "/data/docs/" 下存在 5 个 markdown 文件
    When 用户调用按路径导入接口，路径 "/data/docs/"
    Then 5 个文档全部导入成功
    And 每个文档返回独立的文档 ID
    And 导入结果包含成功数与失败数

  Scenario: 按路径递归导入子目录
    Given 本地目录 "/data/docs/" 下存在子目录，共 10 个文件
    When 用户调用按路径导入接口，路径 "/data/docs/"，递归为 true
    Then 10 个文档全部导入成功
    And 子目录中的文件也被导入

  # ============ Happy Path：文档类型自动检测 ============

  Scenario: 表格类型文档自动检测
    Given 文档内容包含 Markdown 表格语法 "| 列1 | 列2 |"
    When 系统解析该文档
    Then 文档类型识别为 "TABLE"
    And 表格数据被结构化提取

  Scenario: 计划类型文档自动检测
    Given 文档内容包含 "- [ ] 待办事项" 或 "- [x] 已完成"
    When 系统解析该文档
    Then 文档类型识别为 "PLAN"
    And 计划项被提取

  Scenario: 时间线类型文档自动检测
    Given 文档内容包含日期时间模式 "2026-06-28 发生事件"
    When 系统解析该文档
    Then 文档类型识别为 "TIMELINE"
    And 时间线事件被提取

  Scenario: 关系图类型文档自动检测
    Given 文档内容包含 "A -> B" 或 "A 依赖 B" 的关系描述
    When 系统解析该文档
    Then 文档类型识别为 "GRAPH"
    And 依赖关系被提取

  Scenario: 规则类型文档自动检测
    Given 文档内容包含 "规则：" 或 "约束：" 关键字
    When 系统解析该文档
    Then 文档类型识别为 "RULE"
    And 规则条目被提取

  Scenario: 通用类型文档兜底
    Given 文档内容不匹配任何特定类型
    When 系统解析该文档
    Then 文档类型识别为 "GENERAL"
    And 文档按通用 Markdown 处理

  # ============ Happy Path：实体提取 ============

  Scenario: 提取主机实体
    Given 文档内容包含 "主机 192.168.1.100，SSH 端口 22"
    When 系统执行实体提取
    Then kn_host 表写入一条主机记录
    And 主机 IP 为 "192.168.1.100"
    And kn_doc_entity_ref 表记录文档与主机的关联

  Scenario: 提取服务实体
    Given 文档内容包含 "服务 nginx 运行在 192.168.1.100:80"
    When 系统执行实体提取
    Then kn_service 表写入一条服务记录
    And 服务名称为 "nginx"
    And kn_doc_entity_ref 表记录文档与服务的关联

  Scenario: 提取端口实体
    Given 文档内容包含 "端口 8080 被 tomcat 占用"
    When 系统执行实体提取
    Then kn_port 表写入一条端口记录
    And 端口号为 8080

  Scenario: 提取凭据实体
    Given 文档内容包含 "用户名 admin，密码 admin123"
    When 系统执行实体提取
    Then kn_credential 表写入一条凭据记录
    And 凭据用户名为 "admin"
    And 凭据密码加密存储

  Scenario: 提取域名实体
    Given 文档内容包含 "域名 example.com 到期时间 2027-01-01"
    When 系统执行实体提取
    Then kn_domain 表写入一条域名记录
    And 域名为 "example.com"
    And 到期时间为 "2027-01-01"

  Scenario: 提取命令实体
    Given 文档内容包含 "执行命令 systemctl restart nginx"
    When 系统执行实体提取
    Then kn_command 表写入一条命令记录
    And 命令内容为 "systemctl restart nginx"

  # ============ Happy Path：双维度渲染 ============

  Scenario: 机器可读维度渲染
    Given 文档 "测试文档" 已解析完成，提取到 3 个实体
    When 用户调用机器可读维度接口
    Then 返回 JSON 格式的结构化数据
    And 数据包含文档索引信息
    And 数据包含所有提取的实体列表
    And 实体包含类型、ID、属性字段

  Scenario: 人类可读维度渲染
    Given 文档 "测试文档" 已解析完成，提取到 3 个实体
    When 用户调用人类可读维度接口
    Then 返回格式化的 Markdown 内容
    And 内容包含文档原文摘要
    And 内容包含实体关系的人类可读描述
    And 内容包含表格化的实体清单

  Scenario: 双维度联合查询
    Given 文档 "测试文档" 已解析完成
    When 用户同时请求机器可读和人类可读维度
    Then 返回包含两个维度的复合结果
    And 机器可读维度包含结构化数据
    And 人类可读维度包含 Markdown 内容

  # ============ 异常路径 ============

  Scenario: 导入不存在的路径失败
    Given 本地路径 "/data/nonexistent/" 不存在
    When 用户调用按路径导入接口
    Then 返回 404 错误码
    And 错误信息为 "路径不存在"

  Scenario: 导入空目录
    Given 本地目录 "/data/empty/" 存在但为空
    When 用户调用按路径导入接口
    Then 返回空结果
    And 成功数为 0
    And 失败数为 0

  Scenario: 导入不支持的文件类型
    Given 本地路径 "/data/docs/" 下存在 "image.png" 文件
    When 用户调用按路径导入接口
    Then 该文件被跳过
    And 导入结果失败数加 1
    And 失败原因记录为 "不支持的文件类型"

  Scenario: 解析超长文档
    Given 文档内容超过 1MB
    When 系统解析该文档
    Then 返回 400 错误码
    And 错误信息为 "文档内容超出限制"

  Scenario: 实体提取时文档内容为空
    Given 文档内容为空
    When 系统执行实体提取
    Then 不写入任何实体记录
    And 不抛出异常

  Scenario: 重复导入相同文档
    Given 文档 "已导入文档" 已存在于数据库
    When 用户再次导入相同路径的文档
    Then 创建新的文档记录
    And 旧记录保留不删除
    And 新旧记录通过路径关联

  # ============ 边界路径 ============

  Scenario: 导入文件名包含中文
    Given 本地路径 "/data/docs/" 下存在 "运维手册.md" 文件
    When 用户调用按路径导入接口
    Then 文档导入成功
    And 文档标题为 "运维手册"

  Scenario: 导入嵌套深层目录
    Given 本地目录 "/data/a/b/c/d/" 下存在文档
    When 用户递归导入路径 "/data/a/"
    Then 深层目录中的文档被导入
    And 文档路径元数据记录完整层级

  Scenario: 文档类型混合检测
    Given 文档内容同时包含表格和计划项
    When 系统解析该文档
    Then 文档类型识别为首个匹配的类型
    And 其他类型的结构也被提取并保存

  Scenario: 实体提取去重
    Given 文档内容包含多次 "192.168.1.100"
    When 系统执行实体提取
    Then kn_host 表只写入一条 IP 为 "192.168.1.100" 的记录
    And kn_doc_entity_ref 表记录多次关联

  Scenario: 查询文档关联的实体列表
    Given 文档 "测试文档" 已提取到 3 个主机和 2 个服务
    When 用户查询该文档的实体列表
    Then 返回 5 条实体记录
    And 每条记录包含实体类型和详情

  Scenario: 按实体反查文档
    Given 主机 "192.168.1.100" 被 3 个文档提及
    When 用户查询该主机关联的文档列表
    Then 返回 3 条文档记录
    And 每条记录包含文档标题和摘要

  Scenario: 知识搜索
    Given 知识库已导入 10 个文档
    When 用户搜索关键词 "nginx 配置"
    Then 返回包含 "nginx" 或 "配置" 的文档列表
    And 结果按相关度排序
    And 结果包含文档摘要和高亮关键词
