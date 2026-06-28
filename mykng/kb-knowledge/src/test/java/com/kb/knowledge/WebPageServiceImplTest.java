package com.kb.knowledge;

import cn.hutool.http.HttpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kb.common.exception.BusinessException;
import com.kb.common.page.PageResult;
import com.kb.knowledge.dto.web.WebCollectRequest;
import com.kb.knowledge.dto.web.WebMoveRequest;
import com.kb.knowledge.entity.ResourceTag;
import com.kb.knowledge.entity.Version;
import com.kb.knowledge.entity.WebPage;
import com.kb.knowledge.mapper.ResourceTagMapper;
import com.kb.knowledge.mapper.VersionMapper;
import com.kb.knowledge.mapper.WebPageMapper;
import com.kb.knowledge.mongo.doc.WebContent;
import com.kb.knowledge.mongo.repository.WebContentRepository;
import com.kb.knowledge.service.EventPublisher;
import com.kb.knowledge.service.SearchIndexService;
import com.kb.knowledge.service.impl.WebPageServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("网页服务单元测试")
class WebPageServiceImplTest {

    @Mock private WebPageMapper webPageMapper;
    @Mock private VersionMapper versionMapper;
    @Mock private WebContentRepository webContentRepository;
    @Mock private ResourceTagMapper resourceTagMapper;
    @Mock private EventPublisher eventPublisher;
    @Mock private SearchIndexService searchIndexService;

    @InjectMocks
    private WebPageServiceImpl webPageService;

    private WebPage buildWebPage(Long id, Long userId, String url, String title, Integer starred) {
        WebPage wp = new WebPage();
        wp.setId(id);
        wp.setUserId(userId);
        wp.setUrl(url);
        wp.setTitle(title);
        wp.setStarred(starred);
        return wp;
    }

    @Test
    @DisplayName("collect - URL 格式错误")
    void collectInvalidUrl() {
        WebCollectRequest request = new WebCollectRequest();
        request.setUrl("not-a-url");
        request.setFolderId(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> webPageService.collect(1L, request));
        assertTrue(ex.getMessage().contains("URL 格式错误"));
    }

    @Test
    @DisplayName("collect - 仅允许 http/https 协议")
    void collectInvalidProtocol() {
        WebCollectRequest request = new WebCollectRequest();
        request.setUrl("ftp://8.8.8.8/");
        request.setFolderId(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> webPageService.collect(1L, request));
        assertTrue(ex.getMessage().contains("仅允许 http/https 协议"));
    }

    @Test
    @DisplayName("collect - 不允许访问内网回环地址")
    void collectLoopbackAddress() {
        WebCollectRequest request = new WebCollectRequest();
        request.setUrl("http://127.0.0.1/");
        request.setFolderId(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> webPageService.collect(1L, request));
        assertTrue(ex.getMessage().contains("不允许访问内网"));
    }

    @Test
    @DisplayName("collect - 不允许访问保留地址 10.x")
    void collectSiteLocalAddress() {
        WebCollectRequest request = new WebCollectRequest();
        request.setUrl("http://10.0.0.1/");
        request.setFolderId(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> webPageService.collect(1L, request));
        assertTrue(ex.getMessage().contains("不允许访问内网"));
    }

    @Test
    @DisplayName("collect - 不允许访问保留地址 192.168.x")
    void collectSiteLocalAddress192() {
        WebCollectRequest request = new WebCollectRequest();
        request.setUrl("http://192.168.1.1/");
        request.setFolderId(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> webPageService.collect(1L, request));
        assertTrue(ex.getMessage().contains("不允许访问内网"));
    }

    @Test
    @DisplayName("collect - 无法解析主机名")
    void collectUnknownHost() {
        WebCollectRequest request = new WebCollectRequest();
        request.setUrl("http://nonexistent.invalid/");
        request.setFolderId(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> webPageService.collect(1L, request));
        assertTrue(ex.getMessage().contains("无法解析主机名"));
    }

    @Test
    @DisplayName("collect - 正常采集（带 title）")
    void collectNormalWithTitle() {
        WebCollectRequest request = new WebCollectRequest();
        request.setUrl("http://8.8.8.8/");
        request.setFolderId(1L);

        when(webPageMapper.insert(any(WebPage.class))).thenAnswer(invocation -> {
            WebPage wp = invocation.getArgument(0);
            wp.setId(100L);
            return 1;
        });

        try (MockedStatic<HttpUtil> mockedHttpUtil = mockStatic(HttpUtil.class)) {
            mockedHttpUtil.when(() -> HttpUtil.get(eq("http://8.8.8.8/"), anyInt()))
                    .thenReturn("<html><head><title>Test Page</title></head><body></body></html>");

            WebPage result = webPageService.collect(1L, request);

            assertNotNull(result);
            assertEquals("Test Page", result.getTitle());
            assertEquals(0, result.getStarred());
            verify(webContentRepository).save(any(WebContent.class));
            verify(versionMapper).insert(any(Version.class));
            verify(searchIndexService).indexWebPage(any(WebPage.class), anyString());
            verify(eventPublisher).publishKnowledgeEvent(eq(1L), eq("COLLECT"), eq("web"), eq(100L), anyString());
        }
    }

    @Test
    @DisplayName("collect - 正常采集（无 title 时使用 URL 作为标题）")
    void collectNormalWithoutTitle() {
        WebCollectRequest request = new WebCollectRequest();
        request.setUrl("http://8.8.8.8/");
        request.setFolderId(1L);

        when(webPageMapper.insert(any(WebPage.class))).thenAnswer(invocation -> {
            WebPage wp = invocation.getArgument(0);
            wp.setId(100L);
            return 1;
        });

        try (MockedStatic<HttpUtil> mockedHttpUtil = mockStatic(HttpUtil.class)) {
            mockedHttpUtil.when(() -> HttpUtil.get(eq("http://8.8.8.8/"), anyInt()))
                    .thenReturn("<html><body>No title here</body></html>");

            WebPage result = webPageService.collect(1L, request);
            assertEquals("http://8.8.8.8/", result.getTitle());
        }
    }

    @Test
    @DisplayName("collect - HttpUtil 抛异常包装为业务异常")
    void collectHttpUtilThrows() {
        WebCollectRequest request = new WebCollectRequest();
        request.setUrl("http://8.8.8.8/");
        request.setFolderId(1L);

        try (MockedStatic<HttpUtil> mockedHttpUtil = mockStatic(HttpUtil.class)) {
            mockedHttpUtil.when(() -> HttpUtil.get(anyString(), anyInt()))
                    .thenThrow(new RuntimeException("Connection refused"));

            BusinessException ex = assertThrows(BusinessException.class, () -> webPageService.collect(1L, request));
            assertTrue(ex.getMessage().contains("网页采集失败"));
        }
    }

    @Test
    @DisplayName("collect - title 为空时使用 URL 作为标题")
    void collectBlankTitle() {
        WebCollectRequest request = new WebCollectRequest();
        request.setUrl("http://8.8.8.8/");
        request.setFolderId(1L);

        when(webPageMapper.insert(any(WebPage.class))).thenAnswer(invocation -> {
            WebPage wp = invocation.getArgument(0);
            wp.setId(100L);
            return 1;
        });

        try (MockedStatic<HttpUtil> mockedHttpUtil = mockStatic(HttpUtil.class)) {
            mockedHttpUtil.when(() -> HttpUtil.get(anyString(), anyInt()))
                    .thenReturn("<html><head><title>   </title></head></html>");

            WebPage result = webPageService.collect(1L, request);
            assertEquals("http://8.8.8.8/", result.getTitle());
        }
    }

    @Test
    @DisplayName("list - 正常分页")
    void listNormal() {
        Page<WebPage> pageResult = new Page<>(1, 10);
        WebPage wp = buildWebPage(1L, 1L, "http://example.com", "Test", 0);
        pageResult.setRecords(Collections.singletonList(wp));
        pageResult.setTotal(1);
        when(webPageMapper.selectPage(any(), any())).thenReturn(pageResult);

        PageResult<WebPage> result = webPageService.list(1L, null, 1, 10);
        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getList().size());
    }

    @Test
    @DisplayName("list - 带 folderId 过滤")
    void listWithFolderId() {
        Page<WebPage> pageResult = new Page<>(1, 10);
        pageResult.setRecords(Collections.emptyList());
        pageResult.setTotal(0);
        when(webPageMapper.selectPage(any(), any())).thenReturn(pageResult);

        PageResult<WebPage> result = webPageService.list(1L, 5L, 1, 10);
        assertNotNull(result);
        assertEquals(0, result.getTotal());
    }

    @Test
    @DisplayName("getById - 正常")
    void getByIdNormal() {
        WebPage wp = buildWebPage(1L, 1L, "http://example.com", "Test", 0);
        when(webPageMapper.selectById(1L)).thenReturn(wp);

        WebPage result = webPageService.getById(1L, 1L);
        assertNotNull(result);
        assertEquals("Test", result.getTitle());
    }

    @Test
    @DisplayName("getById - 网页不存在")
    void getByIdNotFound() {
        when(webPageMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> webPageService.getById(1L, 1L));
    }

    @Test
    @DisplayName("getById - 无权限")
    void getByIdNoPermission() {
        WebPage wp = buildWebPage(1L, 2L, "http://example.com", "Test", 0);
        when(webPageMapper.selectById(1L)).thenReturn(wp);
        assertThrows(BusinessException.class, () -> webPageService.getById(1L, 1L));
    }

    @Test
    @DisplayName("delete - 正常删除并级联清理")
    void deleteNormal() {
        WebPage wp = buildWebPage(1L, 1L, "http://example.com", "Test", 0);
        when(webPageMapper.selectById(1L)).thenReturn(wp);

        WebContent wc1 = new WebContent();
        wc1.setWebId(1L);
        WebContent wc2 = new WebContent();
        wc2.setWebId(1L);
        when(webContentRepository.findByWebIdOrderByVersionDesc(1L))
                .thenReturn(Arrays.asList(wc1, wc2));

        assertDoesNotThrow(() -> webPageService.delete(1L, 1L));
        verify(webPageMapper).deleteById(1L);
        verify(searchIndexService).removeWebPageIndex(1L);
        verify(webContentRepository, times(2)).delete(any(WebContent.class));
        verify(versionMapper).delete(any());
        verify(resourceTagMapper).delete(any());
        verify(eventPublisher).publishKnowledgeEvent(eq(1L), eq("DELETE"), eq("web"), eq(1L), anyString());
    }

    @Test
    @DisplayName("delete - 无内容时也正常执行")
    void deleteNoContent() {
        WebPage wp = buildWebPage(1L, 1L, "http://example.com", "Test", 0);
        when(webPageMapper.selectById(1L)).thenReturn(wp);
        when(webContentRepository.findByWebIdOrderByVersionDesc(1L))
                .thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> webPageService.delete(1L, 1L));
        verify(webContentRepository, never()).delete(any(WebContent.class));
    }

    @Test
    @DisplayName("delete - 网页不存在")
    void deleteNotFound() {
        when(webPageMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> webPageService.delete(1L, 1L));
    }

    @Test
    @DisplayName("star - 从 0 切换到 1")
    void starToggleOn() {
        WebPage wp = buildWebPage(1L, 1L, "http://example.com", "Test", 0);
        when(webPageMapper.selectById(1L)).thenReturn(wp);

        assertDoesNotThrow(() -> webPageService.star(1L, 1L));
        assertEquals(1, wp.getStarred());
        verify(webPageMapper).updateById(any(WebPage.class));
    }

    @Test
    @DisplayName("star - 从 1 切换到 0")
    void starToggleOff() {
        WebPage wp = buildWebPage(1L, 1L, "http://example.com", "Test", 1);
        when(webPageMapper.selectById(1L)).thenReturn(wp);

        assertDoesNotThrow(() -> webPageService.star(1L, 1L));
        assertEquals(0, wp.getStarred());
    }

    @Test
    @DisplayName("star - 网页不存在")
    void starNotFound() {
        when(webPageMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> webPageService.star(1L, 1L));
    }

    @Test
    @DisplayName("move - 正常移动")
    void moveNormal() {
        WebPage wp = buildWebPage(1L, 1L, "http://example.com", "Test", 0);
        wp.setFolderId(1L);
        when(webPageMapper.selectById(1L)).thenReturn(wp);

        WebMoveRequest request = new WebMoveRequest();
        request.setFolderId(2L);

        assertDoesNotThrow(() -> webPageService.move(1L, 1L, request));
        assertEquals(2L, wp.getFolderId());
        verify(webPageMapper).updateById(any(WebPage.class));
    }

    @Test
    @DisplayName("move - 网页不存在")
    void moveNotFound() {
        when(webPageMapper.selectById(1L)).thenReturn(null);
        WebMoveRequest request = new WebMoveRequest();
        request.setFolderId(2L);
        assertThrows(BusinessException.class, () -> webPageService.move(1L, 1L, request));
    }

    @Test
    @DisplayName("refetch - 正常重新抓取")
    void refetchNormal() {
        WebPage wp = buildWebPage(1L, 1L, "http://8.8.8.8/", "Old Title", 0);
        when(webPageMapper.selectById(1L)).thenReturn(wp);

        WebContent oldContent = new WebContent();
        oldContent.setWebId(1L);
        oldContent.setVersion(1);
        oldContent.setIsCurrent(true);
        when(webContentRepository.findByWebIdAndIsCurrentTrue(1L)).thenReturn(Optional.of(oldContent));

        WebContent latestVersion = new WebContent();
        latestVersion.setWebId(1L);
        latestVersion.setVersion(2);
        when(webContentRepository.findByWebIdOrderByVersionDesc(1L))
                .thenReturn(Collections.singletonList(latestVersion));

        try (MockedStatic<HttpUtil> mockedHttpUtil = mockStatic(HttpUtil.class)) {
            mockedHttpUtil.when(() -> HttpUtil.get(anyString(), anyInt()))
                    .thenReturn("<html><head><title>New Title</title></head></html>");

            WebPage result = webPageService.refetch(1L, 1L);
            assertEquals("New Title", result.getTitle());
            verify(webPageMapper).updateById(any(WebPage.class));
            verify(webContentRepository, atLeast(2)).save(any(WebContent.class));
            verify(searchIndexService).indexWebPage(any(WebPage.class), anyString());
            verify(eventPublisher).publishKnowledgeEvent(eq(1L), eq("REFETCH"), eq("web"), eq(1L), anyString());
        }
    }

    @Test
    @DisplayName("refetch - 无 title 时使用 URL 作为标题")
    void refetchWithoutTitle() {
        WebPage wp = buildWebPage(1L, 1L, "http://8.8.8.8/", "Old Title", 0);
        when(webPageMapper.selectById(1L)).thenReturn(wp);

        when(webContentRepository.findByWebIdAndIsCurrentTrue(1L)).thenReturn(Optional.empty());
        when(webContentRepository.findByWebIdOrderByVersionDesc(1L))
                .thenReturn(Collections.emptyList());

        try (MockedStatic<HttpUtil> mockedHttpUtil = mockStatic(HttpUtil.class)) {
            mockedHttpUtil.when(() -> HttpUtil.get(anyString(), anyInt()))
                    .thenReturn("<html><body>no title</body></html>");

            WebPage result = webPageService.refetch(1L, 1L);
            assertEquals("http://8.8.8.8/", result.getTitle());
        }
    }

    @Test
    @DisplayName("refetch - HttpUtil 抛异常包装为业务异常")
    void refetchHttpUtilThrows() {
        WebPage wp = buildWebPage(1L, 1L, "http://8.8.8.8/", "Old Title", 0);
        when(webPageMapper.selectById(1L)).thenReturn(wp);

        try (MockedStatic<HttpUtil> mockedHttpUtil = mockStatic(HttpUtil.class)) {
            mockedHttpUtil.when(() -> HttpUtil.get(anyString(), anyInt()))
                    .thenThrow(new RuntimeException("Connection refused"));

            BusinessException ex = assertThrows(BusinessException.class, () -> webPageService.refetch(1L, 1L));
            assertTrue(ex.getMessage().contains("网页重新抓取失败"));
        }
    }

    @Test
    @DisplayName("refetch - 网页不存在")
    void refetchNotFound() {
        when(webPageMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> webPageService.refetch(1L, 1L));
    }

    @Test
    @DisplayName("refetch - 无当前版本时正常执行")
    void refetchNoCurrentVersion() {
        WebPage wp = buildWebPage(1L, 1L, "http://8.8.8.8/", "Old Title", 0);
        when(webPageMapper.selectById(1L)).thenReturn(wp);
        when(webContentRepository.findByWebIdAndIsCurrentTrue(1L)).thenReturn(Optional.empty());
        when(webContentRepository.findByWebIdOrderByVersionDesc(1L))
                .thenReturn(Collections.emptyList());

        try (MockedStatic<HttpUtil> mockedHttpUtil = mockStatic(HttpUtil.class)) {
            mockedHttpUtil.when(() -> HttpUtil.get(anyString(), anyInt()))
                    .thenReturn("<html><head><title>New</title></head></html>");

            WebPage result = webPageService.refetch(1L, 1L);
            assertEquals("New", result.getTitle());
            verify(webContentRepository, times(1)).save(any(WebContent.class));
        }
    }

    @Test
    @DisplayName("refetch - URL 无效抛异常")
    void refetchInvalidUrl() {
        WebPage wp = buildWebPage(1L, 1L, "ftp://8.8.8.8/", "Old Title", 0);
        when(webPageMapper.selectById(1L)).thenReturn(wp);

        BusinessException ex = assertThrows(BusinessException.class, () -> webPageService.refetch(1L, 1L));
        assertTrue(ex.getMessage().contains("仅允许 http/https 协议"));
    }
}
