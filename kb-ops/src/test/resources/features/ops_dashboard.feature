# language: zh-CN
# SOP V1.1 阶段2.5 强制要求：BDD 场景文件
# 模块：kb-ops 运维看板
# 覆盖业务流程：FLOW-05 运维看板-矛盾检测、FLOW-08 端口-凭据管理、FLOW-09 域名-到期管理
Feature: 运维看板与矛盾检测
  作为一个运维人员
  我想要管理主机、服务、端口、凭据和域名
  以便维护基础设施的一致性与可见性

  # ============ Happy Path：主机管理 ============

  Scenario: 创建主机成功
    Given 系统中不存在 IP 为 "192.168.1.100" 的主机
    When 用户创建主机，名称 "web-server-01"，IP "192.168.1.100"，SSH 端口 22
    Then 主机创建成功，返回主机 ID
    And SSH 端口默认为 22
    And 主机状态默认为 "启用"
    And 响应中不返回加密密码字段

  Scenario: 创建主机时密码加密存储
    Given 系统中不存在 IP 为 "192.168.1.101" 的主机
    When 用户创建主机，密码 "secret123"
    Then 数据库中 passwordEncrypted 字段存储的是加密后的密文
    And 响应中 passwordEncrypted 字段为 null

  Scenario: 创建主机时指定 SSH 端口
    Given 系统中不存在 IP 为 "192.168.1.102" 的主机
    When 用户创建主机，SSH 端口 2222
    Then 主机创建成功
    And SSH 端口为 2222

  Scenario: 更新主机信息
    Given 主机 "web-server-01" 已存在，ID 为 1
    When 用户更新主机，名称改为 "web-server-01-updated"
    Then 主机名称更新成功
    And IP 地址保持不变

  Scenario: 更新主机密码时不修改其他字段
    Given 主机 "web-server-01" 已存在，密码 "old-pass"
    When 用户更新主机，仅修改密码为 "new-pass"
    Then 数据库中 passwordEncrypted 字段更新为新密文
    And 主机名称保持不变

  Scenario: 删除主机
    Given 主机 "web-server-01" 已存在，ID 为 1
    When 用户删除主机 ID 1
    Then 主机从数据库物理删除

  Scenario: 获取主机列表默认不返回密码
    Given 系统存在 3 台主机
    When 用户查询主机列表
    Then 返回 3 条主机记录
    And 每条记录的 passwordEncrypted 字段为 null

  Scenario: 获取主机详情默认不返回密码
    Given 主机 "web-server-01" 已存在，ID 为 1
    When 用户查询主机 ID 1，revealPassword 为 false
    Then 返回主机详情
    And passwordEncrypted 字段为 null

  Scenario: 获取主机详情显式返回密码
    Given 主机 "web-server-01" 已存在，ID 为 1
    When 用户查询主机 ID 1，revealPassword 为 true
    Then 返回主机详情
    And passwordEncrypted 字段为加密后的密文

  # ============ 异常路径：主机 IP 唯一性 ============

  Scenario: 创建主机时 IP 重复报 409
    Given 系统已存在 IP 为 "192.168.1.100" 的主机
    When 用户创建主机，IP "192.168.1.100"
    Then 返回 409 错误码
    And 错误信息为 "IP 已存在: 192.168.1.100"

  Scenario: 更新主机时 IP 与其他主机冲突报 409
    Given 主机 A IP "192.168.1.100" 已存在
    And 主机 B IP "192.168.1.101" 已存在
    When 用户更新主机 B 的 IP 为 "192.168.1.100"
    Then 返回 409 错误码
    And 错误信息为 "IP 已存在: 192.168.1.100"

  Scenario: 更新主机时 IP 与自身相同不报错
    Given 主机 "web-server-01" IP "192.168.1.100" 已存在，ID 为 1
    When 用户更新主机 ID 1，IP 保持 "192.168.1.100"
    Then 更新成功
    And 不抛出 409 错误

  Scenario: 获取不存在的主机失败
    Given 主机 ID 999 不存在
    When 用户查询主机 ID 999
    Then 返回 404 错误码
    And 错误信息为 "主机不存在"

  Scenario: 删除不存在的主机失败
    Given 主机 ID 999 不存在
    When 用户删除主机 ID 999
    Then 返回 404 错误码

  # ============ Happy Path：端口冲突检测 ============

  Scenario: 端口冲突检测发现冲突
    Given 主机 A "192.168.1.100" 上运行服务占用端口 8080
    And 主机 B "192.168.1.101" 上运行服务占用端口 8080
    When 用户触发端口冲突检测
    Then 返回冲突列表
    And 冲突列表包含端口 8080
    And 冲突列表包含主机 A 和主机 B

  Scenario: 端口冲突检测无冲突
    Given 系统中所有主机端口使用不重复
    When 用户触发端口冲突检测
    Then 返回空冲突列表

  # ============ Happy Path：看板数据刷新 ============

  Scenario: 看板数据刷新
    Given 系统存在 5 台主机、10 个服务、3 个域名
    When 用户刷新看板快照
    Then 看板统计数据更新
    And 快照记录写入 ops_snapshot 表
    And 统计包含主机总数、服务总数、域名总数

  Scenario: 获取看板数据
    Given 看板快照已生成
    When 用户查询看板数据
    Then 返回看板统计信息
    And 包含主机状态分布
    And 包含服务状态分布

  # ============ Happy Path：凭据管理 ============

  Scenario: 创建凭据成功
    Given 用户要创建一个 SSH 凭据
    When 用户创建凭据，名称 "prod-ssh-key"，类型 "SSH"，密码 "secret"
    Then 凭据创建成功，返回凭据 ID
    And 数据库中密码字段存储的是加密后的密文
    And 响应中不返回明文密码

  Scenario: 获取凭据列表默认不返回明文密码
    Given 系统存在 3 个凭据
    When 用户查询凭据列表
    Then 返回 3 条凭据记录
    And 每条记录的明文密码字段为 null

  Scenario: 获取凭据详情显式返回明文密码
    Given 凭据 "prod-ssh-key" 已存在，ID 为 1
    When 用户查询凭据 ID 1，revealPassword 为 true
    Then 返回凭据详情
    And 明文密码字段返回解密后的明文

  Scenario: 更新凭据
    Given 凭据 "prod-ssh-key" 已存在
    When 用户更新凭据，密码改为 "new-secret"
    Then 数据库中密码字段更新为新密文

  Scenario: 删除凭据
    Given 凭据 "prod-ssh-key" 已存在，ID 为 1
    When 用户删除凭据 ID 1
    Then 凭据从数据库物理删除

  # ============ Happy Path：域名到期管理 ============

  Scenario: 创建域名记录
    Given 用户要创建一个域名记录
    When 用户创建域名 "example.com"，到期时间 "2027-01-01"
    Then 域名记录创建成功，返回域名 ID
    And 域名状态为 "有效"

  Scenario: 查询即将过期的域名
    Given 系统存在 3 个域名，其中 1 个将在 30 天内到期
    When 用户查询即将过期的域名列表
    Then 返回 1 条域名记录
    And 该域名状态为 "即将过期"

  Scenario: 更新域名到期时间
    Given 域名 "example.com" 已存在，到期时间 "2027-01-01"
    When 用户更新到期时间为 "2028-01-01"
    Then 域名到期时间更新成功
    And 域名状态变为 "有效"

  Scenario: 删除域名
    Given 域名 "example.com" 已存在，ID 为 1
    When 用户删除域名 ID 1
    Then 域名从数据库物理删除

  # ============ 边界路径 ============

  Scenario: 主机列表关键词搜索
    Given 系统存在主机 "web-server-01"、"db-server-01"、"cache-server-01"
    When 用户搜索关键词 "web"
    Then 返回 1 条主机记录
    And 记录名称为 "web-server-01"

  Scenario: 主机列表按状态筛选
    Given 系统存在 3 台启用主机和 2 台禁用主机
    When 用户筛选状态为 "禁用" 的主机
    Then 返回 2 条主机记录
    And 所有记录状态为 "禁用"

  Scenario: 创建主机时 IP 为空跳过唯一性校验
    When 用户创建主机，IP 为空
    Then 主机创建成功
    And 不进行 IP 唯一性校验

  Scenario: 凭据密码为空时创建失败
    When 用户创建凭据，密码为空
    Then 返回 400 错误码
    And 错误信息为 "密码不能为空"

  Scenario: 域名到期时间早于当前时间
    When 用户创建域名，到期时间早于今天
    Then 返回 400 错误码
    And 错误信息为 "到期时间必须晚于当前时间"

  Scenario: 操作日志记录
    Given 用户执行创建主机操作
    When 主机创建成功
    Then ops_operation_log 表记录一条日志
    And 日志包含 action、userId、操作时间

  Scenario: 看板数据按时间范围查询
    Given 系统在最近 7 天内生成了 7 个快照
    When 用户查询最近 7 天的看板趋势
    Then 返回 7 条快照记录
    And 记录按时间正序排列
