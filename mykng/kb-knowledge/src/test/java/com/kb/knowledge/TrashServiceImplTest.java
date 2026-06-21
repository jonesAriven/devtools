package com.kb.knowledge;

import com.kb.common.page.PageResult;
import com.kb.knowledge.entity.Doc;
import com.kb.knowledge.feign.FileClient;
import com.kb.knowledge.mapper.DocMapper;
import com.kb.knowledge.mapper.WebPageMapper;
import com.kb.knowledge.service.impl.TrashServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Test
    @DisplayName("查询回收站 - 仅文档类型")
    void listTrashDocsOnly() {
        Doc doc = new Doc();
        doc.setId(1L);
        doc.setTitle("已删除文档");
        when(docMapper.selectTrashList(1L)).thenReturn(List.of(doc));

        PageResult<Map<String, Object>> result = trashService.list(1L, "doc", 1, 20);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals("doc", result.getList().get(0).get("type"));
        assertEquals("已删除文档", result.getList().get(0).get("name"));
    }

    @Test
    @DisplayName("查询回收站 - 所有类型（Feign失败时优雅降级）")
    void listTrashAllTypes() {
        Doc doc = new Doc();
        doc.setId(1L);
        doc.setTitle("文档");
        when(docMapper.selectTrashList(1L)).thenReturn(List.of(doc));
        when(fileClient.listTrash(1L)).thenThrow(new RuntimeException("Feign error"));
        when(webPageMapper.selectTrashList(1L)).thenReturn(List.of());

        PageResult<Map<String, Object>> result = trashService.list(1L, null, 1, 20);

        assertNotNull(result);
        // 文件回收站 Feign 失败，但文档回收站仍然返回
        assertTrue(result.getTotal() >= 1);
    }

    @Test
    @DisplayName("查询回收站 - 分页边界")
    void listTrashPagination() {
        Doc d1 = new Doc(); d1.setId(1L); d1.setTitle("文档1");
        Doc d2 = new Doc(); d2.setId(2L); d2.setTitle("文档2");
        Doc d3 = new Doc(); d3.setId(3L); d3.setTitle("文档3");
        when(docMapper.selectTrashList(1L)).thenReturn(List.of(d1, d2, d3));

        // page=1, size=2 → 返回2条
        PageResult<Map<String, Object>> page1 = trashService.list(1L, "doc", 1, 2);
        assertEquals(3, page1.getTotal());
        assertEquals(2, page1.getList().size());

        // page=2, size=2 → 返回1条
        PageResult<Map<String, Object>> page2 = trashService.list(1L, "doc", 2, 2);
        assertEquals(3, page2.getTotal());
        assertEquals(1, page2.getList().size());
    }
}
