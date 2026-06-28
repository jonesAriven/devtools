# language: zh-CN
# SOP V1.1 阶段2.5 强制要求：BDD 场景文件
# 模块：kb-knowledge 文档生命周期管理
# 覆盖业务流程：FLOW-02 知识空间-文档管理
Feature: 文档生命周期管理
  作为一个用户
  我想要管理知识文档
  以便组织我的知识库

  # ============ Happy Path ============

  Scenario: 创建文档成功
    Given 知识空间 "测试空间" 已存在
    When 用户在空间下创建文档 "测试文档"
    Then 文档创建成功，返回文档 ID
    And 文档内容写入 MongoDB
    And 文档索引写入 MeiliSearch
    And 文档状态为 "草稿"

  Scenario: 更新文档创建版本快照
    Given 文档 "测试文档" 已存在，内容为 "v1 内容"
    When 用户更新文档内容为 "v2 内容"
    Then 文档内容更新成功
    And 创建一条版本快照，内容为 "v1 内容"
    And MeiliSearch 索引更新

  Scenario: 删除文档进回收站
    Given 文档 "测试文档" 已存在
    When 用户删除文档
    Then 文档 deleted 字段变为 1
    And 文档出现在回收站列表
    And 文档不在正常列表中

  Scenario: 从回收站恢复文档
    Given 文档 "测试文档" 在回收站中
    When 用户恢复文档
    Then 文档 deleted 字段变为 0
    And 文档不在回收站列表
    And 文档回到正常列表

  Scenario: 版本回滚
    Given 文档 "测试文档" 有 3 个历史版本
    When 用户回滚到版本 2
    Then 文档内容变为版本 2 的内容
    And 创建一条新版本快照记录当前回滚操作

  Scenario: 收藏文档
    Given 文档 "测试文档" 未被收藏
    When 用户收藏文档
    Then 文档 star 字段变为 1
    When 用户再次收藏文档
    Then 文档 star 字段变为 0

  Scenario: 移动文档到其他文件夹
    Given 文档 "测试文档" 在文件夹 "A" 下
    And 文件夹 "B" 已存在
    When 用户移动文档到文件夹 "B"
    Then 文档 folderId 变为文件夹 "B" 的 ID

  # ============ 异常路径 ============

  Scenario: 彻底删除文档
    Given 文档 "测试文档" 在回收站中
    When 用户彻底删除文档
    Then 文档从数据库物理删除
    And MongoDB 内容被删除
    And MeiliSearch 索引被删除

  Scenario: 文档不存在时获取详情失败
    Given 文档 ID 999 不存在
    When 用户获取文档详情
    Then 返回 404 错误码
    And 错误信息为 "文档不存在"

  Scenario: 更新不存在的文档失败
    Given 文档 ID 999 不存在
    When 用户更新文档内容为 "新内容"
    Then 返回 404 错误码

  Scenario: 删除不存在的文档失败
    Given 文档 ID 999 不存在
    When 用户删除文档
    Then 返回 404 错误码

  Scenario: 移动文档到不存在的文件夹失败
    Given 文档 "测试文档" 在文件夹 "A" 下
    And 文件夹 ID 999 不存在
    When 用户移动文档到文件夹 ID 999
    Then 返回 404 错误码
    And 文档 folderId 不变

  # ============ 边界路径 ============

  Scenario: 创建文档时空空间不存在
    Given 知识空间 ID 999 不存在
    When 用户在空间下创建文档 "测试文档"
    Then 返回 404 错误码
    And 错误信息为 "空间不存在"

  Scenario: 创建文档时标题为空
    Given 知识空间 "测试空间" 已存在
    When 用户在空间下创建文档标题为 ""
    Then 返回 400 错误码
    And 错误信息包含 "标题"

  Scenario: 创建文档时内容超长
    Given 知识空间 "测试空间" 已存在
    When 用户创建内容超过 10MB 的文档
    Then 返回 400 错误码
    And 错误信息为 "文档内容超出限制"

  Scenario: 跨用户访问文档失败
    Given 用户 "userA" 的文档 "私有文档" 已存在
    When 用户 "userB" 尝试获取该文档
    Then 返回 403 错误码
    And 错误信息为 "无权访问"

  Scenario: 回收站为空时查询
    Given 用户 "admin" 的回收站没有任何文档
    When 用户查询回收站列表
    Then 返回空列表
    And 总数为 0

  Scenario: 版本回滚到不存在的版本
    Given 文档 "测试文档" 有 3 个历史版本
    When 用户回滚到版本 99
    Then 返回 404 错误码
    And 错误信息为 "版本不存在"

  Scenario: 收藏不存在的文档
    Given 文档 ID 999 不存在
    When 用户收藏文档
    Then 返回 404 错误码

  Scenario: 恢复不在回收站的文档
    Given 文档 "测试文档" 不在回收站中
    When 用户尝试恢复文档
    Then 返回 400 错误码
    And 错误信息为 "文档不在回收站"
