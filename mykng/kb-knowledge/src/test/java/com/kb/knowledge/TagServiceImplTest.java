package com.kb.knowledge;

import com.marschat.common.exception.BusinessException;
import com.kb.knowledge.dto.tag.TagBindRequest;
import com.kb.knowledge.entity.ResourceTag;
import com.kb.knowledge.entity.Tag;
import com.kb.knowledge.mapper.ResourceTagMapper;
import com.kb.knowledge.mapper.TagMapper;
import com.kb.knowledge.service.EventPublisher;
import com.kb.knowledge.service.impl.TagServiceImpl;
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
@DisplayName("标签服务单元测试")
class TagServiceImplTest {

    @Mock private TagMapper tagMapper;
    @Mock private ResourceTagMapper resourceTagMapper;
    @Mock private EventPublisher eventPublisher;

    @InjectMocks
    private TagServiceImpl tagService;

    private Tag buildTag(Long id, Long userId, String name, String color) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setUserId(userId);
        tag.setName(name);
        tag.setColor(color);
        return tag;
    }

    @Test
    @DisplayName("listByUserId - 正常返回列表")
    void listByUserIdNormal() {
        Tag tag = buildTag(1L, 1L, "T1", "red");
        when(tagMapper.selectList(any())).thenReturn(Collections.singletonList(tag));

        List<Tag> result = tagService.listByUserId(1L);
        assertEquals(1, result.size());
        assertEquals("T1", result.get(0).getName());
    }

    @Test
    @DisplayName("listByUserId - 返回空列表")
    void listByUserIdEmpty() {
        when(tagMapper.selectList(any())).thenReturn(Collections.emptyList());
        List<Tag> result = tagService.listByUserId(1L);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("create - 正常创建标签")
    void createNormal() {
        when(tagMapper.selectOne(any())).thenReturn(null);
        when(tagMapper.insert(any(Tag.class))).thenAnswer(invocation -> {
            Tag t = invocation.getArgument(0);
            t.setId(10L);
            return 1;
        });

        Tag result = tagService.create(1L, "新标签", "blue");
        assertNotNull(result);
        assertEquals("新标签", result.getName());
        assertEquals("blue", result.getColor());
        verify(eventPublisher).publishKnowledgeEvent(eq(1L), eq("CREATE"), eq("tag"), eq(10L), anyString());
    }

    @Test
    @DisplayName("create - 标签已存在抛异常")
    void createDuplicate() {
        Tag exist = buildTag(1L, 1L, "已存在", "red");
        when(tagMapper.selectOne(any())).thenReturn(exist);

        BusinessException ex = assertThrows(BusinessException.class, () -> tagService.create(1L, "已存在", "blue"));
        assertTrue(ex.getMessage().contains("标签已存在"));
    }

    @Test
    @DisplayName("update - 正常更新（含名称变更）")
    void updateWithNewName() {
        Tag tag = buildTag(1L, 1L, "Old", "red");
        when(tagMapper.selectById(1L)).thenReturn(tag);
        when(tagMapper.selectOne(any())).thenReturn(null);

        Tag result = tagService.update(1L, 1L, "New", "blue");
        assertEquals("New", result.getName());
        assertEquals("blue", result.getColor());
        verify(tagMapper).updateById(any(Tag.class));
        verify(eventPublisher).publishKnowledgeEvent(eq(1L), eq("UPDATE"), eq("tag"), eq(1L), anyString());
    }

    @Test
    @DisplayName("update - 仅更新颜色")
    void updateOnlyColor() {
        Tag tag = buildTag(1L, 1L, "Old", "red");
        when(tagMapper.selectById(1L)).thenReturn(tag);

        Tag result = tagService.update(1L, 1L, null, "blue");
        assertEquals("Old", result.getName());
        assertEquals("blue", result.getColor());
        verify(tagMapper).updateById(any(Tag.class));
    }

    @Test
    @DisplayName("update - 名称与原名称相同不查重")
    void updateSameName() {
        Tag tag = buildTag(1L, 1L, "Same", "red");
        when(tagMapper.selectById(1L)).thenReturn(tag);

        Tag result = tagService.update(1L, 1L, "Same", "blue");
        assertEquals("Same", result.getName());
        verify(tagMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("update - 名称已存在抛异常")
    void updateDuplicateName() {
        Tag tag = buildTag(1L, 1L, "Old", "red");
        when(tagMapper.selectById(1L)).thenReturn(tag);
        Tag exist = buildTag(2L, 1L, "Exist", "blue");
        when(tagMapper.selectOne(any())).thenReturn(exist);

        BusinessException ex = assertThrows(BusinessException.class, () -> tagService.update(1L, 1L, "Exist", null));
        assertTrue(ex.getMessage().contains("标签名称已存在"));
    }

    @Test
    @DisplayName("update - 标签不存在")
    void updateNotFound() {
        when(tagMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> tagService.update(1L, 1L, "New", null));
    }

    @Test
    @DisplayName("update - 无权限（用户不匹配）")
    void updateNoPermission() {
        Tag tag = buildTag(1L, 2L, "Old", "red");
        when(tagMapper.selectById(1L)).thenReturn(tag);
        assertThrows(BusinessException.class, () -> tagService.update(1L, 1L, "New", null));
    }

    @Test
    @DisplayName("delete - 正常删除标签及关联")
    void deleteNormal() {
        Tag tag = buildTag(1L, 1L, "T", "red");
        when(tagMapper.selectById(1L)).thenReturn(tag);

        assertDoesNotThrow(() -> tagService.delete(1L, 1L));
        verify(tagMapper).deleteById(1L);
        verify(resourceTagMapper).delete(any());
        verify(eventPublisher).publishKnowledgeEvent(eq(1L), eq("DELETE"), eq("tag"), eq(1L), anyString());
    }

    @Test
    @DisplayName("delete - 标签不存在")
    void deleteNotFound() {
        when(tagMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> tagService.delete(1L, 1L));
    }

    @Test
    @DisplayName("delete - 无权限")
    void deleteNoPermission() {
        Tag tag = buildTag(1L, 2L, "T", "red");
        when(tagMapper.selectById(1L)).thenReturn(tag);
        assertThrows(BusinessException.class, () -> tagService.delete(1L, 1L));
    }

    @Test
    @DisplayName("bind - 正常绑定资源")
    void bindNormal() {
        Tag tag = buildTag(1L, 1L, "T", "red");
        when(tagMapper.selectById(1L)).thenReturn(tag);
        when(resourceTagMapper.selectOne(any())).thenReturn(null);

        TagBindRequest request = new TagBindRequest();
        request.setTagId(1L);
        request.setResourceType("doc");
        request.setResourceId(100L);

        assertDoesNotThrow(() -> tagService.bind(1L, request));
        verify(resourceTagMapper).insert(any(ResourceTag.class));
    }

    @Test
    @DisplayName("bind - 已绑定直接返回（幂等）")
    void bindAlreadyExists() {
        Tag tag = buildTag(1L, 1L, "T", "red");
        when(tagMapper.selectById(1L)).thenReturn(tag);
        ResourceTag exist = new ResourceTag();
        exist.setId(99L);
        when(resourceTagMapper.selectOne(any())).thenReturn(exist);

        TagBindRequest request = new TagBindRequest();
        request.setTagId(1L);
        request.setResourceType("doc");
        request.setResourceId(100L);

        assertDoesNotThrow(() -> tagService.bind(1L, request));
        verify(resourceTagMapper, never()).insert(any(ResourceTag.class));
    }

    @Test
    @DisplayName("bind - 标签不存在")
    void bindTagNotFound() {
        when(tagMapper.selectById(1L)).thenReturn(null);

        TagBindRequest request = new TagBindRequest();
        request.setTagId(1L);
        request.setResourceType("doc");
        request.setResourceId(100L);

        assertThrows(BusinessException.class, () -> tagService.bind(1L, request));
    }

    @Test
    @DisplayName("bind - 无权限")
    void bindNoPermission() {
        Tag tag = buildTag(1L, 2L, "T", "red");
        when(tagMapper.selectById(1L)).thenReturn(tag);

        TagBindRequest request = new TagBindRequest();
        request.setTagId(1L);
        request.setResourceType("doc");
        request.setResourceId(100L);

        assertThrows(BusinessException.class, () -> tagService.bind(1L, request));
    }

    @Test
    @DisplayName("unbind - 正常解绑")
    void unbindNormal() {
        assertDoesNotThrow(() -> tagService.unbind(1L, 1L, "doc", 100L));
        verify(resourceTagMapper).delete(any());
    }

    @Test
    @DisplayName("getTagsByResource - 资源存在多个标签")
    void getTagsByResourceNormal() {
        ResourceTag rt1 = new ResourceTag();
        rt1.setTagId(1L);
        ResourceTag rt2 = new ResourceTag();
        rt2.setTagId(2L);
        when(resourceTagMapper.selectList(any())).thenReturn(Arrays.asList(rt1, rt2));

        Tag t1 = buildTag(1L, 1L, "T1", "red");
        Tag t2 = buildTag(2L, 1L, "T2", "blue");
        when(tagMapper.selectList(any())).thenReturn(Arrays.asList(t1, t2));

        List<Tag> result = tagService.getTagsByResource(1L, 100L, "doc");
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("getTagsByResource - 无标签返回空")
    void getTagsByResourceEmpty() {
        when(resourceTagMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<Tag> result = tagService.getTagsByResource(1L, 100L, "doc");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
