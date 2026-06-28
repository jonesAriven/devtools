# language: zh-CN
# SOP V1.1 阶段2.5 强制要求：BDD 场景文件
# 模块：kb-knowledge 分享访问
# 覆盖业务流程：FLOW-04 分享-访问
Feature: 分享访问管理
  作为一个用户
  我想要将文档分享给他人
  以便在无需登录的情况下公开访问知识内容

  # ============ Happy Path ============

  Scenario: 创建无密码分享
    Given 文档 "测试文档" 已存在
    When 用户创建分享，不设置密码，不过期
    Then 分享创建成功，返回分享 code
    And 分享记录写入数据库
    And 分享状态为 "有效"

  Scenario: 创建有密码分享
    Given 文档 "测试文档" 已存在
    When 用户创建分享，密码为 "1234"，有效期 7 天
    Then 分享创建成功，返回分享 code
    And 分享记录包含加密后的密码
    And 分享过期时间为 7 天后

  Scenario: 创建带过期时间的分享
    Given 文档 "测试文档" 已存在
    When 用户创建分享，不设密码，有效期 1 天
    Then 分享创建成功，返回分享 code
    And 分享过期时间为 1 天后

  Scenario: 无密码分享直接访问
    Given 存在一个无密码的分享 "share-abc"
    When 访客访问分享 "share-abc"，不提供密码
    Then 返回分享内容
    And 记录访问日志

  Scenario: 有密码分享验证成功
    Given 存在一个有密码的分享 "share-xyz"，密码为 "1234"
    When 访客访问分享 "share-xyz"，提供密码 "1234"
    Then 返回分享内容
    And 记录访问日志

  Scenario: 查看我的分享列表
    Given 用户 "admin" 创建过 3 个分享
    When 用户查询我的分享列表
    Then 返回 3 条分享记录
    And 列表按创建时间倒序排列

  Scenario: 撤销分享
    Given 存在一个有效的分享 "share-abc"
    When 用户撤销分享 "share-abc"
    Then 分享状态变为 "已撤销"
    And 访客访问该分享返回 410 错误

  # ============ 异常路径 ============

  Scenario: 密码错误访问失败
    Given 存在一个有密码的分享 "share-xyz"，密码为 "1234"
    When 访客访问分享 "share-xyz"，提供密码 "9999"
    Then 返回 403 错误码
    And 错误信息为 "提取码错误"
    And 不返回分享内容

  Scenario: 有密码分享未提供密码访问失败
    Given 存在一个有密码的分享 "share-xyz"，密码为 "1234"
    When 访客访问分享 "share-xyz"，不提供密码
    Then 返回 403 错误码
    And 错误信息为 "需要提取码"

  Scenario: 分享过期访问失败
    Given 存在一个已过期的分享 "share-expired"
    When 访客访问分享 "share-expired"
    Then 返回 410 错误码
    And 错误信息为 "分享已过期"

  Scenario: 分享已撤销访问失败
    Given 存在一个已撤销的分享 "share-revoked"
    When 访客访问分享 "share-revoked"
    Then 返回 410 错误码
    And 错误信息为 "分享已撤销"

  Scenario: 分享 code 不存在
    Given 分享 code "nonexistent-code" 不存在
    When 访客访问分享 "nonexistent-code"
    Then 返回 404 错误码
    And 错误信息为 "分享不存在"

  Scenario: 分享不存在的文档失败
    Given 文档 ID 999 不存在
    When 用户尝试为该文档创建分享
    Then 返回 404 错误码
    And 错误信息为 "文档不存在"

  Scenario: 跨用户撤销分享失败
    Given 用户 "userA" 创建了分享 "share-a"
    When 用户 "userB" 尝试撤销分享 "share-a"
    Then 返回 403 错误码
    And 错误信息为 "无权操作"

  # ============ 边界路径 ============

  Scenario: 分享密码为空字符串
    Given 文档 "测试文档" 已存在
    When 用户创建分享，密码为 ""
    Then 分享创建成功，视为无密码分享
    And 访客可直接访问

  Scenario: 分享过期时间为 0 天
    Given 文档 "测试文档" 已存在
    When 用户创建分享，有效期 0 天
    Then 返回 400 错误码
    And 错误信息为 "过期时间必须大于 0"

  Scenario: 重复撤销已撤销的分享
    Given 存在一个已撤销的分享 "share-revoked"
    When 用户再次撤销分享 "share-revoked"
    Then 返回 400 错误码
    And 错误信息为 "分享已撤销"

  Scenario: 撤销不存在的分享
    Given 分享 ID 999 不存在
    When 用户撤销分享 ID 999
    Then 返回 404 错误码

  Scenario: 创建分享时文档已被删除
    Given 文档 "测试文档" 已被逻辑删除
    When 用户尝试为该文档创建分享
    Then 返回 404 错误码
    And 错误信息为 "文档不存在"

  Scenario: 访客连续错误密码 5 次后被限流
    Given 存在一个有密码的分享 "share-xyz"，密码为 "1234"
    When 访客连续 5 次使用错误密码访问
    Then 第 6 次访问返回 429 错误码
    And 错误信息为 "尝试次数过多，请稍后再试"

  Scenario: 分享列表分页查询
    Given 用户 "admin" 创建过 25 个分享
    When 用户查询我的分享列表，第 2 页，每页 10 条
    Then 返回 10 条分享记录
    And 总数为 25
    And 当前页码为 2
