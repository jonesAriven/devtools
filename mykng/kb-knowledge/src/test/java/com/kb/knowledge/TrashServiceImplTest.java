package com.kb.knowledge;

import com.kb.common.exception.BusinessException;
import com.kb.common.exception.NoPermissionException;
import com.kb.common.page.PageResult;
import com.kb.common.result.Result;
import com.kb.knowledge.entity.Doc;
import com.kb.knowledge.entity.WebPage;
import com.kb.knowledge.feign.FileClient;
import com.kb.knowledge.feign.dto.FileDTO;
import com.kb.knowledge.mapper.DocMapper;
import com.kb.knowledge.mapper.WebPageMapper;
import com.kb.knowledge.service.impl.TrashServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("回收站服务单元测试")
class TrashServiceImplTest {

    @Mock private DocMapper docMapper;
    @Mock private WebPageMapper webPageMapper;
    @Mock private FileClient fileClient;

    @InjectMocks
    private TrashServiceImpl trashService;

    private Doc buildDoc(Long id, Long userId, String title) {
        Doc doc = new Doc();
        doc.setId(id);
        doc.setUserId(userId);
        doc.setTitle(title);
        return doc;
    }

    private WebPage buildWebPage(Long id, Long userId, String title) {
        WebPage wp = new WebPage();
        wp.setId(id);
        wp.setUserId(userId);
        wp.setTitle(title);
        return wp;
    }

    @Test
    @DisplayName("list - 仅文档类型")
    void listDocsOnly() {
        Doc doc = buildDoc(1L, 1L, "已删除文档");
        when(docMapper.selectTrashList(1L)).thenReturn(List.of(doc));

        PageResult<Map<String, Object>> result = trashService.list(1L, "doc", 1, 20);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals("doc", result.getList().get(0).get("type"));
        assertEquals("已删除文档", result.getList().get(0).get("name"));
    }

    @Test
    @DisplayName("list - 仅网页类型")
    void listWebOnly() {
        WebPage wp = buildWebPage(1L, 1L, "已删除网页");
        when(webPageMapper.selectTrashList(1L)).thenReturn(List.of(wp));

        PageResult<Map<String, Object>> result = trashService.list(1L, "web", 1, 20);
        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals("web", result.getList().get(0).get("type"));
    }

    @Test
    @DisplayName("list - 仅文件类型（Feign成功）")
    void listFileOnlyFeignSuccess() {
        FileDTO f = new FileDTO();
        f.setId(1L);
        f.setName("file.txt");
        when(fileClient.listTrash(1L)).thenReturn(Result.ok(List.of(f)));

        PageResult<Map<String, Object>> result = trashService.list(1L, "file", 1, 20);
        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals("file", result.getList().get(0).get("type"));
    }

    @Test
    @DisplayName("list - 文件类型 Feign 返回 null 数据")
    void listFileFeignNullData() {
        Result<List<FileDTO>> r = Result.ok(null);
        when(fileClient.listTrash(1L)).thenReturn(r);

        PageResult<Map<String, Object>> result = trashService.list(1L, "file", 1, 20);
        assertNotNull(result);
        assertEquals(0, result.getTotal());
    }

    @Test
    @DisplayName("list - 文件类型 Feign 返回非 200")
    void listFileFeignNon200() {
        Result<List<FileDTO>> r = Result.fail(500, "fail");
        when(fileClient.listTrash(1L)).thenReturn(r);

        PageResult<Map<String, Object>> result = trashService.list(1L, "file", 1, 20);
        assertNotNull(result);
        assertEquals(0, result.getTotal());
    }

    @Test
    @DisplayName("list - 文件类型 Feign 抛异常（优雅降级）")
    void listFileFeignException() {
        when(fileClient.listTrash(1L)).thenThrow(new RuntimeException("Feign error"));

        PageResult<Map<String, Object>> result = trashService.list(1L, "file", 1, 20);
        assertNotNull(result);
        assertEquals(0, result.getTotal());
    }

    @Test
    @DisplayName("list - 所有类型混合")
    void listAllTypes() {
        Doc doc = buildDoc(1L, 1L, "文档");
        when(docMapper.selectTrashList(1L)).thenReturn(List.of(doc));
        when(webPageMapper.selectTrashList(1L)).thenReturn(List.of());
        when(fileClient.listTrash(1L)).thenThrow(new RuntimeException("Feign error"));

        PageResult<Map<String, Object>> result = trashService.list(1L, null, 1, 20);
        assertNotNull(result);
        assertTrue(result.getTotal() >= 1);
    }

    @Test
    @DisplayName("list - type 为空字符串（不匹配任何类型，返回空）")
    void listEmptyType() {
        // 空字符串 toLowerCase 后仍为空字符串，不匹配 file/doc/web，返回空
        PageResult<Map<String, Object>> result = trashService.list(1L, "", 1, 20);
        assertNotNull(result);
        assertEquals(0, result.getTotal());
    }

    @Test
    @DisplayName("list - 大写类型 DOC")
    void listUpperCaseType() {
        Doc doc = buildDoc(1L, 1L, "doc");
        when(docMapper.selectTrashList(1L)).thenReturn(List.of(doc));

        PageResult<Map<String, Object>> result = trashService.list(1L, "DOC", 1, 20);
        assertNotNull(result);
        assertEquals(1, result.getTotal());
    }

    @Test
    @DisplayName("list - 分页边界（page1 size2 返回2条）")
    void listPaginationPage1() {
        Doc d1 = buildDoc(1L, 1L, "doc1");
        Doc d2 = buildDoc(2L, 1L, "doc2");
        Doc d3 = buildDoc(3L, 1L, "doc3");
        when(docMapper.selectTrashList(1L)).thenReturn(Arrays.asList(d1, d2, d3));

        PageResult<Map<String, Object>> page1 = trashService.list(1L, "doc", 1, 2);
        assertEquals(3, page1.getTotal());
        assertEquals(2, page1.getList().size());
    }

    @Test
    @DisplayName("list - 分页边界（page2 size2 返回1条）")
    void listPaginationPage2() {
        Doc d1 = buildDoc(1L, 1L, "doc1");
        Doc d2 = buildDoc(2L, 1L, "doc2");
        Doc d3 = buildDoc(3L, 1L, "doc3");
        when(docMapper.selectTrashList(1L)).thenReturn(Arrays.asList(d1, d2, d3));

        PageResult<Map<String, Object>> page2 = trashService.list(1L, "doc", 2, 2);
        assertEquals(3, page2.getTotal());
        assertEquals(1, page2.getList().size());
    }

    @Test
    @DisplayName("list - 分页超出范围返回空")
    void listPaginationOutOfRange() {
        Doc d1 = buildDoc(1L, 1L, "doc1");
        when(docMapper.selectTrashList(1L)).thenReturn(List.of(d1));

        PageResult<Map<String, Object>> result = trashService.list(1L, "doc", 10, 5);
        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }

    @Test
    @DisplayName("restore - 文档正常")
    void restoreDocNormal() {
        Doc doc = buildDoc(1L, 1L, "doc");
        when(docMapper.selectDeletedById(1L)).thenReturn(doc);

        assertDoesNotThrow(() -> trashService.restore(1L, "doc", 1L));
        verify(docMapper).restoreById(1L);
    }

    @Test
    @DisplayName("restore - 文档不存在")
    void restoreDocNotFound() {
        when(docMapper.selectDeletedById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> trashService.restore(1L, "doc", 1L));
    }

    @Test
    @DisplayName("restore - 文档无权限")
    void restoreDocNoPermission() {
        Doc doc = buildDoc(1L, 2L, "doc");
        when(docMapper.selectDeletedById(1L)).thenReturn(doc);
        assertThrows(BusinessException.class, () -> trashService.restore(1L, "doc", 1L));
    }

    @Test
    @DisplayName("restore - 网页正常")
    void restoreWebNormal() {
        WebPage wp = buildWebPage(1L, 1L, "web");
        when(webPageMapper.selectDeletedById(1L)).thenReturn(wp);

        assertDoesNotThrow(() -> trashService.restore(1L, "web", 1L));
        verify(webPageMapper).restoreById(1L);
    }

    @Test
    @DisplayName("restore - 网页不存在")
    void restoreWebNotFound() {
        when(webPageMapper.selectDeletedById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> trashService.restore(1L, "web", 1L));
    }

    @Test
    @DisplayName("restore - 网页无权限")
    void restoreWebNoPermission() {
        WebPage wp = buildWebPage(1L, 2L, "web");
        when(webPageMapper.selectDeletedById(1L)).thenReturn(wp);
        assertThrows(BusinessException.class, () -> trashService.restore(1L, "web", 1L));
    }

    @Test
    @DisplayName("restore - 文件正常")
    void restoreFileNormal() {
        assertDoesNotThrow(() -> trashService.restore(1L, "file", 1L));
        verify(fileClient).restore(1L);
    }

    @Test
    @DisplayName("restore - 文件 Feign 异常包装为业务异常")
    void restoreFileFeignException() {
        doThrow(new RuntimeException("Feign error")).when(fileClient).restore(1L);
        BusinessException ex = assertThrows(BusinessException.class, () -> trashService.restore(1L, "file", 1L));
        assertTrue(ex.getMessage().contains("恢复文件失败"));
    }

    @Test
    @DisplayName("restore - 不支持的资源类型")
    void restoreUnsupportedType() {
        assertThrows(BusinessException.class, () -> trashService.restore(1L, "unknown", 1L));
    }

    @Test
    @DisplayName("restore - type 为 null 抛 NullPointerException")
    void restoreNullType() {
        // switch(null) 会抛 NullPointerException
        assertThrows(NullPointerException.class, () -> trashService.restore(1L, null, 1L));
    }

    @Test
    @DisplayName("permanentDelete - 文档正常")
    void permanentDeleteDocNormal() {
        Doc doc = buildDoc(1L, 1L, "doc");
        when(docMapper.selectDeletedById(1L)).thenReturn(doc);

        assertDoesNotThrow(() -> trashService.permanentDelete(1L, "doc", 1L));
        verify(docMapper).physicalDeleteById(1L);
    }

    @Test
    @DisplayName("permanentDelete - 文档不存在")
    void permanentDeleteDocNotFound() {
        when(docMapper.selectDeletedById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> trashService.permanentDelete(1L, "doc", 1L));
    }

    @Test
    @DisplayName("permanentDelete - 文档无权限抛 NoPermissionException")
    void permanentDeleteDocNoPermission() {
        Doc doc = buildDoc(1L, 2L, "doc");
        when(docMapper.selectDeletedById(1L)).thenReturn(doc);
        assertThrows(NoPermissionException.class, () -> trashService.permanentDelete(1L, "doc", 1L));
    }

    @Test
    @DisplayName("permanentDelete - 网页正常")
    void permanentDeleteWebNormal() {
        WebPage wp = buildWebPage(1L, 1L, "web");
        when(webPageMapper.selectDeletedById(1L)).thenReturn(wp);

        assertDoesNotThrow(() -> trashService.permanentDelete(1L, "web", 1L));
        verify(webPageMapper).physicalDeleteById(1L);
    }

    @Test
    @DisplayName("permanentDelete - 网页不存在")
    void permanentDeleteWebNotFound() {
        when(webPageMapper.selectDeletedById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> trashService.permanentDelete(1L, "web", 1L));
    }

    @Test
    @DisplayName("permanentDelete - 网页无权限")
    void permanentDeleteWebNoPermission() {
        WebPage wp = buildWebPage(1L, 2L, "web");
        when(webPageMapper.selectDeletedById(1L)).thenReturn(wp);
        assertThrows(NoPermissionException.class, () -> trashService.permanentDelete(1L, "web", 1L));
    }

    @Test
    @DisplayName("permanentDelete - 文件正常")
    void permanentDeleteFileNormal() {
        assertDoesNotThrow(() -> trashService.permanentDelete(1L, "file", 1L));
        verify(fileClient).permanentDelete(1L);
    }

    @Test
    @DisplayName("permanentDelete - 文件 Feign 异常包装为业务异常")
    void permanentDeleteFileFeignException() {
        doThrow(new RuntimeException("Feign error")).when(fileClient).permanentDelete(1L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> trashService.permanentDelete(1L, "file", 1L));
        assertTrue(ex.getMessage().contains("永久删除文件失败"));
    }

    @Test
    @DisplayName("permanentDelete - 不支持的资源类型")
    void permanentDeleteUnsupportedType() {
        assertThrows(BusinessException.class, () -> trashService.permanentDelete(1L, "unknown", 1L));
    }

    @Test
    @DisplayName("permanentDelete - type 为 null 抛 NullPointerException")
    void permanentDeleteNullType() {
        // switch(null) 会抛 NullPointerException
        assertThrows(NullPointerException.class, () -> trashService.permanentDelete(1L, null, 1L));
    }

    @Test
    @DisplayName("empty - 正常清空（Feign 异常被吞掉）")
    void emptyNormal() {
        doThrow(new RuntimeException("Feign error")).when(fileClient).emptyTrash(1L);

        assertDoesNotThrow(() -> trashService.empty(1L));
        verify(docMapper).physicalDeleteAllByUserId(1L);
        verify(webPageMapper).physicalDeleteAllByUserId(1L);
    }

    @Test
    @DisplayName("empty - Feign 正常调用")
    void emptyFeignSuccess() {
        when(fileClient.emptyTrash(1L)).thenReturn(Result.ok());

        assertDoesNotThrow(() -> trashService.empty(1L));
        verify(docMapper).physicalDeleteAllByUserId(1L);
        verify(webPageMapper).physicalDeleteAllByUserId(1L);
    }
}
