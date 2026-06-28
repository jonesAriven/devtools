# language: zh-CN
# SOP V1.1 阶段2.5 强制要求：BDD 场景文件
# 模块：kb-file 文件上传与搜索
# 覆盖业务流程：FLOW-03 文件上传-搜索
Feature: 文件上传与搜索
  作为一个用户
  我想要上传文件并搜索内容
  以便快速找到需要的文件资料

  # ============ Happy Path ============

  Scenario: 上传小文件成功
    Given 用户 "admin" 已登录
    When 用户上传文件 "test.txt"（1KB）
    Then 文件元数据写入数据库
    And 文件存储到 MinIO
    And 返回文件 ID

  Scenario: 上传大文件分片成功
    Given 用户 "admin" 已登录
    When 用户上传文件 "big.pdf"（100MB），分 10 片上传
    Then 所有分片上传成功
    And 每个分片返回分片标识

  Scenario: 合并分片触发异步解析
    Given 用户 "admin" 已上传文件 "big.pdf" 的 10 个分片
    When 用户调用合并接口
    Then 文件合并成功，返回 KbFile
    And 触发异步解析任务
    And 解析状态为 "待解析"

  Scenario: 解析状态流转待解析到已解析
    Given 文件 "test.pdf" 解析状态为 "待解析"
    When 异步解析任务开始执行
    Then 解析状态变为 "解析中"
    When 解析任务完成
    Then 解析状态变为 "已解析"
    And 解析内容写入 MongoDB
    And MeiliSearch 索引建立

  Scenario: 重新解析文件
    Given 文件 "test.pdf" 解析状态为 "已解析"
    When 用户重新解析文件
    Then 解析状态重置为 "待解析"
    And 旧的解析内容被覆盖
    And MeiliSearch 索引更新

  Scenario: 关键词搜索命中文件
    Given 文件 "test.pdf" 已解析，内容包含 "知识库架构"
    When 用户搜索关键词 "知识库"
    Then 返回包含 "test.pdf" 的结果列表
    And 结果中包含文件名和摘要

  Scenario: 逻辑删除文件
    Given 文件 "test.pdf" 已存在
    When 用户删除文件
    Then 文件 deleted 字段变为 1
    And 文件不在正常列表中
    And MinIO 对象保留

  Scenario: 物理删除文件
    Given 文件 "test.pdf" 已被逻辑删除
    When 用户彻底删除文件
    Then 文件从数据库物理删除
    And MinIO 对象被删除
    And MongoDB 解析内容被删除
    And MeiliSearch 索引被删除

  Scenario: 收藏文件
    Given 文件 "test.pdf" 未被收藏
    When 用户收藏文件
    Then 文件 star 字段变为 1
    When 用户再次收藏文件
    Then 文件 star 字段变为 0

  # ============ 异常路径 ============

  Scenario: 解析失败状态流转
    Given 文件 "broken.pdf" 解析状态为 "待解析"
    When 异步解析任务执行失败
    Then 解析状态变为 "解析失败"
    And 记录失败原因

  Scenario: 搜索无结果
    Given 数据库中没有任何文件
    When 用户搜索关键词 "不存在的关键词"
    Then 返回空列表
    And 总数为 0

  Scenario: 搜索特殊字符不报错
    Given 文件 "test.pdf" 已解析
    When 用户搜索关键词 "%_\\"
    Then 不抛出异常
    And 返回空列表或匹配结果

  Scenario: 上传空文件失败
    Given 用户 "admin" 已登录
    When 用户上传空文件 "empty.txt"（0KB）
    Then 返回 400 错误码
    And 错误信息为 "文件不能为空"

  Scenario: 上传不支持的文件类型失败
    Given 用户 "admin" 已登录
    When 用户上传文件 "test.exe"
    Then 返回 400 错误码
    And 错误信息为 "不支持的文件类型"

  Scenario: 合并分片时缺少分片失败
    Given 用户 "admin" 上传文件 "big.pdf" 应有 10 个分片，实际只上传 8 个
    When 用户调用合并接口
    Then 返回 400 错误码
    And 错误信息为 "分片不完整"

  Scenario: 重新解析不存在的文件失败
    Given 文件 ID 999 不存在
    When 用户重新解析文件
    Then 返回 404 错误码

  Scenario: 删除不存在的文件失败
    Given 文件 ID 999 不存在
    When 用户删除文件
    Then 返回 404 错误码

  Scenario: 获取不存在的文件下载链接失败
    Given 文件 ID 999 不存在
    When 用户获取下载链接
    Then 返回 404 错误码

  # ============ 边界路径 ============

  Scenario: 上传文件名包含特殊字符
    Given 用户 "admin" 已登录
    When 用户上传文件 "测试 文件 (1).pdf"
    Then 文件上传成功
    And 文件名正确存储

  Scenario: 解析状态查询
    Given 文件 "test.pdf" 已上传但未合并
    When 用户查询解析状态
    Then 返回状态 "未解析"

  Scenario: 获取解析内容
    Given 文件 "test.pdf" 解析状态为 "已解析"
    When 用户获取文件解析内容
    Then 返回解析后的文本内容
    And 内容来自 MongoDB

  Scenario: 获取未解析文件的解析内容失败
    Given 文件 "test.pdf" 解析状态为 "待解析"
    When 用户获取文件解析内容
    Then 返回 400 错误码
    And 错误信息为 "文件尚未解析完成"

  Scenario: 跨用户删除文件失败
    Given 用户 "userA" 上传了文件 "private.pdf"
    When 用户 "userB" 尝试删除该文件
    Then 返回 403 错误码
    And 错误信息为 "无权操作"

  Scenario: 文件列表分页查询
    Given 用户 "admin" 上传了 25 个文件
    When 用户查询文件列表，第 1 页，每页 10 条
    Then 返回 10 条文件记录
    And 总数为 25
    And 列表按创建时间倒序排列

  Scenario: 上传超大文件超过限制
    Given 用户 "admin" 已登录
    When 用户上传文件 "huge.zip"（超过 1GB）
    Then 返回 413 错误码
    And 错误信息为 "文件大小超出限制"

  Scenario: 分片上传中断后恢复
    Given 用户 "admin" 上传文件 "big.pdf" 已完成 5 个分片
    When 用户继续上传剩余 5 个分片
    Then 所有分片上传成功
    And 合并后文件完整

  Scenario: 移动文件到其他文件夹
    Given 文件 "test.pdf" 在文件夹 "A" 下
    And 文件夹 "B" 已存在
    When 用户移动文件到文件夹 "B"
    Then 文件 folderId 变为文件夹 "B" 的 ID
