package com.kb.knowledge.feign;

import com.kb.common.result.Result;
import com.kb.knowledge.feign.dto.FileDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * kb-file Feign 客户端
 * <p>
 * 用于跨服务获取文件信息、下载链接、解析内容。
 * 注意：架构上 kb-knowledge → kb-file 通过事件解耦索引构建，
 * 但分享详情等读操作需要获取文件元数据，故通过 Feign 调用。
 */
@FeignClient(name = "kb-file", url = "${kb.feign.file-url:http://kb-file:8082}")
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
     * 调用 kb-file 的内容接口（需 kb-file 暴露 /api/file/{id}/content 端点）
     */
    @GetMapping("/file/{id}/content")
    Result<String> getContent(@PathVariable("id") Long id);

    /**
     * 列出用户已删除的文件（回收站）
     * 调用 kb-file 的回收站列表接口（需 kb-file 暴露 /api/file/trash 端点）
     */
    @GetMapping("/file/trash")
    Result<java.util.List<FileDTO>> listTrash(@RequestParam("userId") Long userId);

    /**
     * 恢复已删除的文件
     * 调用 kb-file 的恢复接口（需 kb-file 暴露 /api/file/{id}/restore 端点）
     */
    @GetMapping("/file/{id}/restore")
    Result<Void> restore(@PathVariable("id") Long id);

    /**
     * 永久删除文件
     * 调用 kb-file 的永久删除接口（需 kb-file 暴露 /api/file/{id}/permanent 端点）
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/file/{id}/permanent")
    Result<Void> permanentDelete(@PathVariable("id") Long id);

    @org.springframework.web.bind.annotation.DeleteMapping("/file/trash/empty")
    Result<Void> emptyTrash(@RequestParam("userId") Long userId);
}
