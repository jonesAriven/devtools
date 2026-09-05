package com.kb.knowledge;

import com.marschat.common.exception.BusinessException;
import com.kb.knowledge.dto.space.SpaceCreateRequest;
import com.kb.knowledge.dto.space.SpaceUpdateRequest;
import com.kb.knowledge.entity.Space;
import com.kb.knowledge.mapper.SpaceMapper;
import com.kb.knowledge.service.EventPublisher;
import com.kb.knowledge.service.impl.SpaceServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("空间服务单元测试")
class SpaceServiceImplTest {

    @Mock private SpaceMapper spaceMapper;
    @Mock private EventPublisher eventPublisher;

    @InjectMocks
    private SpaceServiceImpl spaceService;

    private Space buildSpace(Long id, Long userId, String name, String type) {
        Space space = new Space();
        space.setId(id);
        space.setUserId(userId);
        space.setName(name);
        space.setType(type);
        space.setStatus(1);
        return space;
    }

    @Test
    @DisplayName("listByUserId - 正常返回列表")
    void listByUserIdNormal() {
        Space space = buildSpace(1L, 1L, "S1", "private");
        when(spaceMapper.selectList(any())).thenReturn(Collections.singletonList(space));

        List<Space> result = spaceService.listByUserId(1L);
        assertEquals(1, result.size());
        assertEquals("S1", result.get(0).getName());
    }

    @Test
    @DisplayName("listByUserId - 空列表")
    void listByUserIdEmpty() {
        when(spaceMapper.selectList(any())).thenReturn(Collections.emptyList());
        List<Space> result = spaceService.listByUserId(1L);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getById - 正常")
    void getByIdNormal() {
        Space space = buildSpace(1L, 1L, "S1", "private");
        when(spaceMapper.selectById(1L)).thenReturn(space);

        Space result = spaceService.getById(1L, 1L);
        assertNotNull(result);
        assertEquals("S1", result.getName());
    }

    @Test
    @DisplayName("getById - 空间不存在")
    void getByIdNotFound() {
        when(spaceMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> spaceService.getById(1L, 1L));
    }

    @Test
    @DisplayName("getById - 无权限")
    void getByIdNoPermission() {
        Space space = buildSpace(1L, 2L, "S1", "private");
        when(spaceMapper.selectById(1L)).thenReturn(space);
        assertThrows(BusinessException.class, () -> spaceService.getById(1L, 999L));
    }

    @Test
    @DisplayName("create - 正常创建（带类型）")
    void createWithDefaultType() {
        SpaceCreateRequest request = new SpaceCreateRequest();
        request.setName("新空间");
        request.setType("public");
        request.setDescription("描述");

        when(spaceMapper.insert(any(Space.class))).thenAnswer(invocation -> {
            Space s = invocation.getArgument(0);
            s.setId(1L);
            return 1;
        });

        Space result = spaceService.create(1L, request);
        assertNotNull(result);
        assertEquals("新空间", result.getName());
        assertEquals("public", result.getType());
        assertEquals("描述", result.getDescription());
        assertEquals(1, result.getStatus());
        verify(eventPublisher).publishKnowledgeEvent(eq(1L), eq("CREATE"), eq("space"), eq(1L), anyString());
    }

    @Test
    @DisplayName("create - 类型为 null 默认 private")
    void createNullType() {
        SpaceCreateRequest request = new SpaceCreateRequest();
        request.setName("新空间");
        request.setType(null);

        when(spaceMapper.insert(any(Space.class))).thenAnswer(invocation -> {
            Space s = invocation.getArgument(0);
            s.setId(1L);
            return 1;
        });

        Space result = spaceService.create(1L, request);
        assertEquals("private", result.getType());
    }

    @Test
    @DisplayName("update - 正常更新所有字段")
    void updateAllFields() {
        Space space = buildSpace(1L, 1L, "Old", "private");
        when(spaceMapper.selectById(1L)).thenReturn(space);

        SpaceUpdateRequest request = new SpaceUpdateRequest();
        request.setName("New");
        request.setType("public");
        request.setDescription("NewDesc");
        request.setStatus(0);

        Space result = spaceService.update(1L, 1L, request);
        assertEquals("New", result.getName());
        assertEquals("public", result.getType());
        assertEquals("NewDesc", result.getDescription());
        assertEquals(0, result.getStatus());
        verify(spaceMapper).updateById(any(Space.class));
    }

    @Test
    @DisplayName("update - 仅更新 name（其他字段为 null）")
    void updateOnlyName() {
        Space space = buildSpace(1L, 1L, "Old", "private");
        space.setDescription("OldDesc");
        space.setStatus(1);
        when(spaceMapper.selectById(1L)).thenReturn(space);

        SpaceUpdateRequest request = new SpaceUpdateRequest();
        request.setName("New");

        Space result = spaceService.update(1L, 1L, request);
        assertEquals("New", result.getName());
        assertEquals("private", result.getType());
        assertEquals("OldDesc", result.getDescription());
        assertEquals(1, result.getStatus());
    }

    @Test
    @DisplayName("update - 空间不存在")
    void updateNotFound() {
        when(spaceMapper.selectById(1L)).thenReturn(null);

        SpaceUpdateRequest request = new SpaceUpdateRequest();
        request.setName("New");

        assertThrows(BusinessException.class, () -> spaceService.update(1L, 1L, request));
    }

    @Test
    @DisplayName("update - 无权限")
    void updateNoPermission() {
        Space space = buildSpace(1L, 2L, "Old", "private");
        when(spaceMapper.selectById(1L)).thenReturn(space);

        SpaceUpdateRequest request = new SpaceUpdateRequest();
        request.setName("New");

        assertThrows(BusinessException.class, () -> spaceService.update(1L, 999L, request));
    }

    @Test
    @DisplayName("delete - 正常删除")
    void deleteNormal() {
        Space space = buildSpace(1L, 1L, "S1", "private");
        when(spaceMapper.selectById(1L)).thenReturn(space);

        assertDoesNotThrow(() -> spaceService.delete(1L, 1L));
        verify(spaceMapper).deleteById(1L);
        verify(eventPublisher).publishKnowledgeEvent(eq(1L), eq("DELETE"), eq("space"), eq(1L), anyString());
    }

    @Test
    @DisplayName("delete - 空间不存在")
    void deleteNotFound() {
        when(spaceMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> spaceService.delete(1L, 1L));
    }

    @Test
    @DisplayName("delete - 无权限")
    void deleteNoPermission() {
        Space space = buildSpace(1L, 2L, "S1", "private");
        when(spaceMapper.selectById(1L)).thenReturn(space);
        assertThrows(BusinessException.class, () -> spaceService.delete(1L, 999L));
    }
}
