package com.kb.ops;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marschat.common.exception.NotFoundException;
import com.marschat.common.page.PageResult;
import com.kb.ops.dto.OpsKnowledgeRequest;
import com.kb.ops.entity.OpsKnowledge;
import com.kb.ops.mapper.OpsKnowledgeMapper;
import com.kb.ops.service.impl.OpsKnowledgeServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("运维知识服务单元测试")
class OpsKnowledgeServiceImplTest {

    @Mock
    private OpsKnowledgeMapper knowledgeMapper;

    @InjectMocks
    private OpsKnowledgeServiceImpl knowledgeService;

    @Test
    @DisplayName("list_无过滤条件_返回分页结果")
    void list_noFilter_returnsPagedResult() {
        OpsKnowledge k = new OpsKnowledge();
        k.setId(1L);
        k.setTitle("部署手册");
        Page<OpsKnowledge> page = new Page<>(1, 20);
        page.setRecords(List.of(k));
        page.setTotal(1);
        when(knowledgeMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<OpsKnowledge> result = knowledgeService.list(null, null, null, null, 1, 20);

        assertEquals(1, result.getTotal());
        assertEquals("部署手册", result.getList().get(0).getTitle());
    }

    @Test
    @DisplayName("list_带关键字分类主机服务_应用全部过滤条件")
    void list_withAllFilters_appliesFilters() {
        OpsKnowledge k = new OpsKnowledge();
        k.setId(1L);
        Page<OpsKnowledge> page = new Page<>(1, 20);
        page.setRecords(List.of(k));
        page.setTotal(1);
        when(knowledgeMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<OpsKnowledge> result = knowledgeService.list("部署", "巡检", 1L, 2L, 1, 20);

        assertEquals(1, result.getTotal());
    }

    @Test
    @DisplayName("getById_存在且阅读量为空_阅读量置为1")
    void getById_exists_viewCountNull_setsTo1() {
        OpsKnowledge k = new OpsKnowledge();
        k.setId(1L);
        k.setTitle("部署手册");
        k.setViewCount(null);
        when(knowledgeMapper.selectById(1L)).thenReturn(k);
        when(knowledgeMapper.incrementViewCount(1L)).thenReturn(1);

        OpsKnowledge result = knowledgeService.getById(1L);

        assertEquals("部署手册", result.getTitle());
        assertEquals(1, result.getViewCount());
        verify(knowledgeMapper).incrementViewCount(1L);
    }

    @Test
    @DisplayName("getById_存在且阅读量非空_阅读量加1")
    void getById_exists_viewCountNotNull_increments() {
        OpsKnowledge k = new OpsKnowledge();
        k.setId(1L);
        k.setViewCount(5);
        when(knowledgeMapper.selectById(1L)).thenReturn(k);
        when(knowledgeMapper.incrementViewCount(1L)).thenReturn(1);

        OpsKnowledge result = knowledgeService.getById(1L);

        assertEquals(6, result.getViewCount());
    }

    @Test
    @DisplayName("getById_不存在_抛出NotFoundException")
    void getById_notFound_throwsNotFoundException() {
        when(knowledgeMapper.selectById(999L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> knowledgeService.getById(999L));
    }

    @Test
    @DisplayName("create_正常创建_阅读量置0")
    void create_normal_setsViewCount0() {
        OpsKnowledgeRequest request = new OpsKnowledgeRequest();
        request.setTitle("部署手册");
        request.setCategory("部署");
        request.setContent("内容");
        request.setTags("tag1,tag2");
        request.setHostId(1L);
        request.setServiceId(2L);
        request.setAuthor("admin");
        when(knowledgeMapper.insert(any(OpsKnowledge.class))).thenAnswer(invocation -> {
            OpsKnowledge k = invocation.getArgument(0);
            k.setId(1L);
            return 1;
        });

        OpsKnowledge result = knowledgeService.create(request);

        assertEquals("部署手册", result.getTitle());
        assertEquals("部署", result.getCategory());
        assertEquals(0, result.getViewCount());
        verify(knowledgeMapper).insert(any(OpsKnowledge.class));
    }

    @Test
    @DisplayName("update_存在_更新成功")
    void update_exists_updates() {
        OpsKnowledge existing = new OpsKnowledge();
        existing.setId(1L);
        existing.setTitle("old");
        when(knowledgeMapper.selectById(1L)).thenReturn(existing);
        when(knowledgeMapper.updateById(any(OpsKnowledge.class))).thenReturn(1);

        OpsKnowledgeRequest request = new OpsKnowledgeRequest();
        request.setTitle("new-title");
        request.setCategory("巡检");

        OpsKnowledge result = knowledgeService.update(1L, request);

        assertEquals("new-title", result.getTitle());
        assertEquals("巡检", result.getCategory());
        verify(knowledgeMapper).updateById(any(OpsKnowledge.class));
    }

    @Test
    @DisplayName("update_不存在_抛出NotFoundException")
    void update_notFound_throwsNotFoundException() {
        when(knowledgeMapper.selectById(999L)).thenReturn(null);

        OpsKnowledgeRequest request = new OpsKnowledgeRequest();
        request.setTitle("new");

        assertThrows(NotFoundException.class, () -> knowledgeService.update(999L, request));
    }

    @Test
    @DisplayName("delete_存在_删除成功")
    void delete_exists_deletes() {
        OpsKnowledge existing = new OpsKnowledge();
        existing.setId(1L);
        when(knowledgeMapper.selectById(1L)).thenReturn(existing);
        when(knowledgeMapper.deleteById(1L)).thenReturn(1);

        assertDoesNotThrow(() -> knowledgeService.delete(1L));

        verify(knowledgeMapper).deleteById(1L);
    }

    @Test
    @DisplayName("delete_不存在_抛出NotFoundException")
    void delete_notFound_throwsNotFoundException() {
        when(knowledgeMapper.selectById(999L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> knowledgeService.delete(999L));
    }
}
