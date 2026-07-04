package com.kb.knowledge.feign;

import com.kb.common.result.Result;
import com.kb.knowledge.feign.dto.FileDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * kb-file Feign 客户端
 * <p>
 * 用于跨服务获取文件信息、下载链接、解析内容。
 * <p>
 * M5-1：去掉 url 参数，Feign 通过 Nacos 服务发现自动路由到 kb-file 实例。
 * 这样 kb-file 可水平扩展、可拔插：下线时 Nacos 自动感知，Feign 调用失败时业务层降级。
 * <p>
 * M4-7：回收站 4 个端点已补齐，FileClient 与 kb-file Controller 完全对齐。
 * M4-5：文件解析完成等非用户主动操作已改事件驱动（EventBus + Redis Streams）。
 */
@FeignClient(name = "kb-file")
public interface FileClient {

    /**
     * 根据文件 ID 获取文件信息
     */
    @GetMapping("/file/{id}")
    Result<FileDTO> getById(@PathVariable("id") Long id);

    /**
     * 获取文件下载链接（预签名 URL）
     */
    @GetMapping("/file/{id}/download")
    Result<String> getDownloadUrl(@PathVariable("id") Long id);

    /**
     * 获取文件解析后的文本内容
     */
    @GetMapping("/file/{id}/content")
    Result<String> getContent(@PathVariable("id") Long id);

    /**
     * 列出用户已删除的文件（回收站）
     */
    @GetMapping("/file/trash")
    Result<List<FileDTO>> listTrash(@RequestParam("userId") Long userId);

    /**
     * 恢复已删除的文件（M4-7：改为 PUT 方法，符合 RESTful 规范）
     */
    @PutMapping("/file/{id}/restore")
    Result<Void> restore(@PathVariable("id") Long id);

    /**
     * 永久删除文件（M4-7 新增对齐）
     */
    @DeleteMapping("/file/{id}/permanent")
    Result<Void> permanentDelete(@PathVariable("id") Long id);

    /**
     * 清空回收站（M4-7 新增对齐）
     */
    @DeleteMapping("/file/trash/empty")
    Result<Void> emptyTrash(@RequestParam("userId") Long userId);

    @GetMapping("/file/search")
    Result<List<FileDTO>> searchByName(@RequestParam("keyword") String keyword,
                                       @RequestParam(value = "folderId", required = false) Long folderId);

    /**
     * 查询当前用户所有文件（供资源树聚合使用）
     */
    @GetMapping("/file/list-all")
    Result<List<FileDTO>> listAll();
}
