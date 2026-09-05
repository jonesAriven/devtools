package com.kb.knowledge;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marschat.common.exception.BusinessException;
import com.marschat.common.page.PageResult;
import com.marschat.common.result.Result;
import com.kb.knowledge.dto.share.ShareCreateRequest;
import com.kb.knowledge.entity.*;
import com.kb.knowledge.feign.FileClient;
import com.kb.knowledge.feign.dto.FileDTO;
import com.kb.knowledge.mapper.*;
import com.kb.knowledge.mongo.doc.DocContent;
import com.kb.knowledge.mongo.doc.WebContent;
import com.kb.knowledge.mongo.repository.DocContentRepository;
import com.kb.knowledge.mongo.repository.WebContentRepository;
import com.kb.knowledge.service.EventPublisher;
import com.kb.knowledge.service.impl.ShareServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("分享服务单元测试")
class ShareServiceImplTest {

    @Mock private ShareMapper shareMapper;
    @Mock private ShareAccessLogMapper shareAccessLogMapper;
    @Mock private DocMapper docMapper;
    @Mock private WebPageMapper webPageMapper;
    @Mock private FolderMapper folderMapper;
    @Mock private DocContentRepository docContentRepository;
    @Mock private WebContentRepository webContentRepository;
    @Mock private FileClient fileClient;
    @Mock private EventPublisher eventPublisher;

    @InjectMocks
    private ShareServiceImpl shareService;

    private Share buildShare(Long id, Long userId, String type, Long resourceId, String code, String extractCode) {
        Share share = new Share();
        share.setId(id);
        share.setUserId(userId);
        share.setResourceType(type);
        share.setResourceId(resourceId);
        share.setCode(code);
        share.setExtractCode(extractCode);
        share.setViewCount(0);
        return share;
    }

    @Test
    @DisplayName("create - 自动生成 code 和 extractCode")
    void createAutoGenerateCode() {
        ShareCreateRequest request = new ShareCreateRequest();
        request.setResourceType("doc");
        request.setResourceId(100L);

        when(shareMapper.insert(any(Share.class))).thenAnswer(invocation -> {
            Share s = invocation.getArgument(0);
            s.setId(1L);
            return 1;
        });

        Share result = shareService.create(1L, request);
        assertNotNull(result);
        assertNotNull(result.getCode());
        assertNotNull(result.getExtractCode());
        assertEquals(0, result.getViewCount());
        verify(eventPublisher).publishKnowledgeEvent(eq(1L), eq("SHARE"), eq("doc"), eq(100L), anyString());
    }

    @Test
    @DisplayName("create - 自定义提取码")
    void createWithCustomExtractCode() {
        ShareCreateRequest request = new ShareCreateRequest();
        request.setResourceType("doc");
        request.setResourceId(100L);
        request.setExtractCode("1234");

        when(shareMapper.insert(any(Share.class))).thenAnswer(invocation -> {
            Share s = invocation.getArgument(0);
            s.setId(1L);
            return 1;
        });

        Share result = shareService.create(1L, request);
        assertEquals("1234", result.getExtractCode());
    }

    @Test
    @DisplayName("create - 设置过期时间（ISO 格式带空格）")
    void createWithExpireAtSpaceFormat() {
        ShareCreateRequest request = new ShareCreateRequest();
        request.setResourceType("doc");
        request.setResourceId(100L);
        request.setExpireAt("2026-12-31 23:59:59");

        when(shareMapper.insert(any(Share.class))).thenAnswer(invocation -> {
            Share s = invocation.getArgument(0);
            s.setId(1L);
            return 1;
        });

        Share result = shareService.create(1L, request);
        assertNotNull(result.getExpireAt());
        assertEquals(LocalDateTime.parse("2026-12-31T23:59:59"), result.getExpireAt());
    }

    @Test
    @DisplayName("create - 设置过期时间（ISO T 格式）")
    void createWithExpireAtISOFormat() {
        ShareCreateRequest request = new ShareCreateRequest();
        request.setResourceType("doc");
        request.setResourceId(100L);
        request.setExpireAt("2026-12-31T23:59:59");

        when(shareMapper.insert(any(Share.class))).thenAnswer(invocation -> {
            Share s = invocation.getArgument(0);
            s.setId(1L);
            return 1;
        });

        Share result = shareService.create(1L, request);
        assertNotNull(result.getExpireAt());
    }

    @Test
    @DisplayName("create - 过期时间为空字符串")
    void createWithEmptyExpireAt() {
        ShareCreateRequest request = new ShareCreateRequest();
        request.setResourceType("doc");
        request.setResourceId(100L);
        request.setExpireAt("");

        when(shareMapper.insert(any(Share.class))).thenAnswer(invocation -> {
            Share s = invocation.getArgument(0);
            s.setId(1L);
            return 1;
        });

        Share result = shareService.create(1L, request);
        assertNull(result.getExpireAt());
    }

    @Test
    @DisplayName("list - 正常分页")
    void listNormal() {
        Page<Share> pageResult = new Page<>(1, 10);
        Share share = buildShare(1L, 1L, "doc", 100L, "code", "1234");
        pageResult.setRecords(Collections.singletonList(share));
        pageResult.setTotal(1);
        when(shareMapper.selectPage(any(), any())).thenReturn(pageResult);

        PageResult<Share> result = shareService.list(1L, 1, 10);
        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getList().size());
    }

    @Test
    @DisplayName("listMyShares - 正常返回")
    void listMySharesNormal() {
        Share share = buildShare(1L, 1L, "doc", 100L, "code", "1234");
        when(shareMapper.selectList(any())).thenReturn(Collections.singletonList(share));

        List<Share> result = shareService.listMyShares(1L);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("listMyShares - 空列表")
    void listMySharesEmpty() {
        when(shareMapper.selectList(any())).thenReturn(Collections.emptyList());
        List<Share> result = shareService.listMyShares(1L);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("delete - 正常删除")
    void deleteNormal() {
        Share share = buildShare(1L, 1L, "doc", 100L, "code", "1234");
        when(shareMapper.selectById(1L)).thenReturn(share);

        assertDoesNotThrow(() -> shareService.delete(1L, 1L));
        verify(shareMapper).deleteById(1L);
        verify(eventPublisher).publishKnowledgeEvent(eq(1L), eq("DELETE"), eq("share"), eq(1L), anyString());
    }

    @Test
    @DisplayName("delete - 分享不存在")
    void deleteNotFound() {
        when(shareMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> shareService.delete(1L, 1L));
    }

    @Test
    @DisplayName("delete - 无权限")
    void deleteNoPermission() {
        Share share = buildShare(1L, 2L, "doc", 100L, "code", "1234");
        when(shareMapper.selectById(1L)).thenReturn(share);
        assertThrows(BusinessException.class, () -> shareService.delete(1L, 1L));
    }

    @Test
    @DisplayName("verify - 正常验证（无提取码）")
    void verifyNormalNoExtractCode() {
        Share share = buildShare(1L, 1L, "doc", 100L, "code", null);
        when(shareMapper.selectOne(any())).thenReturn(share);

        Share result = shareService.verify("code", null);
        assertNotNull(result);
        verify(shareMapper).update(eq(null), any());
    }

    @Test
    @DisplayName("verify - 正确提取码")
    void verifyCorrectExtractCode() {
        Share share = buildShare(1L, 1L, "doc", 100L, "code", "1234");
        when(shareMapper.selectOne(any())).thenReturn(share);

        Share result = shareService.verify("code", "1234");
        assertNotNull(result);
    }

    @Test
    @DisplayName("verify - 分享不存在")
    void verifyShareNotFound() {
        when(shareMapper.selectOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> shareService.verify("code", null));
    }

    @Test
    @DisplayName("verify - 分享已过期")
    void verifyExpired() {
        Share share = buildShare(1L, 1L, "doc", 100L, "code", null);
        share.setExpireAt(LocalDateTime.now().minusDays(1));
        when(shareMapper.selectOne(any())).thenReturn(share);

        BusinessException ex = assertThrows(BusinessException.class, () -> shareService.verify("code", null));
        assertTrue(ex.getMessage().contains("已过期"));
    }

    @Test
    @DisplayName("verify - 提取码错误")
    void verifyWrongExtractCode() {
        Share share = buildShare(1L, 1L, "doc", 100L, "code", "1234");
        when(shareMapper.selectOne(any())).thenReturn(share);

        BusinessException ex = assertThrows(BusinessException.class, () -> shareService.verify("code", "9999"));
        assertTrue(ex.getMessage().contains("提取码错误"));
    }

    @Test
    @DisplayName("getDetail - 文档类型分享")
    void getDetailDoc() {
        Share share = buildShare(1L, 1L, "doc", 100L, "code", null);
        when(shareMapper.selectOne(any())).thenReturn(share);

        Doc doc = new Doc();
        doc.setId(100L);
        doc.setUserId(1L);
        when(docMapper.selectById(100L)).thenReturn(doc);

        DocContent content = new DocContent();
        content.setDocId(100L);
        when(docContentRepository.findByDocIdAndIsCurrentTrue(100L)).thenReturn(Optional.of(content));

        Object result = shareService.getDetail("code", null);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<?, ?> map = (Map<?, ?>) result;
        assertNotNull(map.get("share"));
        assertNotNull(map.get("resource"));
        assertNotNull(map.get("content"));
    }

    @Test
    @DisplayName("getDetail - 文档已被删除")
    void getDetailDocDeleted() {
        Share share = buildShare(1L, 1L, "doc", 100L, "code", null);
        when(shareMapper.selectOne(any())).thenReturn(share);
        when(docMapper.selectById(100L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> shareService.getDetail("code", null));
        assertTrue(ex.getMessage().contains("已被删除"));
    }

    @Test
    @DisplayName("getDetail - 网页类型分享")
    void getDetailWeb() {
        Share share = buildShare(1L, 1L, "web", 100L, "code", null);
        when(shareMapper.selectOne(any())).thenReturn(share);

        WebPage wp = new WebPage();
        wp.setId(100L);
        wp.setUserId(1L);
        when(webPageMapper.selectById(100L)).thenReturn(wp);

        WebContent content = new WebContent();
        content.setWebId(100L);
        when(webContentRepository.findByWebIdAndIsCurrentTrue(100L)).thenReturn(Optional.of(content));

        Object result = shareService.getDetail("code", null);
        assertNotNull(result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertNotNull(map.get("share"));
        assertNotNull(map.get("resource"));
        assertNotNull(map.get("content"));
    }

    @Test
    @DisplayName("getDetail - 网页已被删除")
    void getDetailWebDeleted() {
        Share share = buildShare(1L, 1L, "web", 100L, "code", null);
        when(shareMapper.selectOne(any())).thenReturn(share);
        when(webPageMapper.selectById(100L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> shareService.getDetail("code", null));
    }

    @Test
    @DisplayName("getDetail - 文件类型分享")
    void getDetailFile() {
        Share share = buildShare(1L, 1L, "file", 100L, "code", null);
        when(shareMapper.selectOne(any())).thenReturn(share);

        FileDTO fileDTO = new FileDTO();
        fileDTO.setId(100L);
        fileDTO.setName("file.txt");
        when(fileClient.getById(100L)).thenReturn(Result.ok(fileDTO));
        when(fileClient.getDownloadUrl(100L)).thenReturn(Result.ok("http://example.com/download"));
        when(fileClient.getContent(100L)).thenReturn(Result.ok("file content"));

        Object result = shareService.getDetail("code", null);
        assertNotNull(result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertNotNull(map.get("share"));
        assertNotNull(map.get("resource"));
        assertNotNull(map.get("downloadUrl"));
        assertNotNull(map.get("content"));
    }

    @Test
    @DisplayName("getDetail - 文件类型 Feign 异常被吞掉")
    void getDetailFileFeignException() {
        Share share = buildShare(1L, 1L, "file", 100L, "code", null);
        when(shareMapper.selectOne(any())).thenReturn(share);

        when(fileClient.getById(100L)).thenThrow(new RuntimeException("Feign error"));

        Object result = shareService.getDetail("code", null);
        assertNotNull(result);
        Map<?, ?> map = (Map<?, ?>) result;
        // 仅有 share，其他字段都没有
        assertNotNull(map.get("share"));
        assertNull(map.get("resource"));
    }

    @Test
    @DisplayName("getDetail - 文件类型 Feign 返回 null 数据")
    void getDetailFileNullData() {
        Share share = buildShare(1L, 1L, "file", 100L, "code", null);
        when(shareMapper.selectOne(any())).thenReturn(share);

        when(fileClient.getById(100L)).thenReturn(Result.ok(null));
        when(fileClient.getDownloadUrl(100L)).thenReturn(Result.ok(null));
        when(fileClient.getContent(100L)).thenReturn(Result.ok(null));

        Object result = shareService.getDetail("code", null);
        assertNotNull(result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertNotNull(map.get("share"));
        assertNull(map.get("resource"));
        assertNull(map.get("downloadUrl"));
    }

    @Test
    @DisplayName("getDetail - 文件类型 Feign 返回非 200")
    void getDetailFileNon200() {
        Share share = buildShare(1L, 1L, "file", 100L, "code", null);
        when(shareMapper.selectOne(any())).thenReturn(share);

        when(fileClient.getById(100L)).thenReturn(Result.fail(500, "fail"));
        when(fileClient.getDownloadUrl(100L)).thenReturn(Result.fail(500, "fail"));
        when(fileClient.getContent(100L)).thenReturn(Result.fail(500, "fail"));

        Object result = shareService.getDetail("code", null);
        assertNotNull(result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertNotNull(map.get("share"));
        assertNull(map.get("resource"));
    }

    @Test
    @DisplayName("getDetail - 文件夹类型分享")
    void getDetailFolder() {
        Share share = buildShare(1L, 1L, "folder", 100L, "code", null);
        when(shareMapper.selectOne(any())).thenReturn(share);

        Folder folder = new Folder();
        folder.setId(100L);
        when(folderMapper.selectById(100L)).thenReturn(folder);

        Object result = shareService.getDetail("code", null);
        assertNotNull(result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertNotNull(map.get("share"));
        assertNotNull(map.get("resource"));
    }

    @Test
    @DisplayName("getDetail - 文件夹已被删除")
    void getDetailFolderDeleted() {
        Share share = buildShare(1L, 1L, "folder", 100L, "code", null);
        when(shareMapper.selectOne(any())).thenReturn(share);
        when(folderMapper.selectById(100L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> shareService.getDetail("code", null));
    }

    @Test
    @DisplayName("getDetail - 网页类型分享无内容")
    void getDetailWebNoContent() {
        Share share = buildShare(1L, 1L, "web", 100L, "code", null);
        when(shareMapper.selectOne(any())).thenReturn(share);

        WebPage wp = new WebPage();
        wp.setId(100L);
        wp.setUserId(1L);
        when(webPageMapper.selectById(100L)).thenReturn(wp);
        when(webContentRepository.findByWebIdAndIsCurrentTrue(100L)).thenReturn(Optional.empty());

        Object result = shareService.getDetail("code", null);
        assertNotNull(result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertNotNull(map.get("share"));
        assertNotNull(map.get("resource"));
        assertNull(map.get("content"));
    }

    @Test
    @DisplayName("getDetail - 文档类型分享无内容")
    void getDetailDocNoContent() {
        Share share = buildShare(1L, 1L, "doc", 100L, "code", null);
        when(shareMapper.selectOne(any())).thenReturn(share);

        Doc doc = new Doc();
        doc.setId(100L);
        doc.setUserId(1L);
        when(docMapper.selectById(100L)).thenReturn(doc);
        when(docContentRepository.findByDocIdAndIsCurrentTrue(100L)).thenReturn(Optional.empty());

        Object result = shareService.getDetail("code", null);
        assertNotNull(result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertNotNull(map.get("share"));
        assertNotNull(map.get("resource"));
        assertNull(map.get("content"));
    }
}
