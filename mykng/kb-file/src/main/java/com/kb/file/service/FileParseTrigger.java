package com.kb.file.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 文件解析异步触发器（独立 Bean，修复 @Async 自调用问题）
 * <p>
 * 原单体代码中 KbFileServiceImpl.triggerAsyncParse() 被 this 调用，
 * 导致 Spring AOP 代理不生效，@Async 注解被忽略。
 * <p>
 * 修复方案：将异步触发逻辑拆分到独立的 Bean 中，通过依赖注入调用，
 * 确保 @Async 代理正常生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileParseTrigger {

    private final FileParseService fileParseService;

    /**
     * 异步触发文件解析
     * <p>
     * 通过独立 Bean 调用，Spring 代理正常拦截 @Async 注解。
     */
    @Async("kbAsyncExecutor")
    public void trigger(Long fileId, String minioPath, String fileType) {
        log.info("异步触发文件解析 fileId={} type={}", fileId, fileType);
        fileParseService.parseFile(fileId, minioPath, fileType);
    }
}
