package com.kb.knowledge;

import com.kb.common.exception.BusinessException;
import com.kb.knowledge.dto.tag.TagBindRequest;
import com.kb.knowledge.entity.ResourceTag;
import com.kb.knowledge.entity.Tag;
import com.kb.knowledge.mapper.ResourceTagMapper;
import com.kb.knowledge.mapper.TagMapper;
import com.kb.knowledge.service.EventPublisher;
import com.kb.knowledge.service.impl.TagServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
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

    @Test
    @DisplayName("创建标签 - 正常创建")
    void createTag() {
        when(tagMapper.selectOne(any())).thenReturn(null);
        when(tagMapper.insert(any(Tag.class))).thenAnswer(invocation -> {
            Tag t = invocation.getArgument(0);
            t.setId(1L);
            return 1;
        });

        Tag result = tagService.create(1L, "重要", "#ff0000");

        assertNotNull(result);
        assertEquals("重要", result.getName());
        verify(eventPublisher).publishKnowledgeEvent(eq(1L), eq("CREATE"), eq("tag"), any(), anyString());
    }

    @Test
    @DisplayName("创建标签 - 标签已存在")
    void createTagDuplicate() {
        Tag existing = new Tag();
        existing.setId(1L);
        existing.setName("重要");
        when(tagMapper.selectOne(any())).thenReturn(existing);

        assertThrows(BusinessException.class, () -> tagService.create(1L, "重要", "#ff0000"));
    }

    @Test
    @DisplayName("删除标签 - 同时删除关联")
    void deleteTag() {
        Tag tag = new Tag();
        tag.setId(1L);
        tag.setUserId(1L);
        tag.setName("重要");
        when(tagMapper.selectById(1L)).thenReturn(tag);

        assertDoesNotThrow(() -> tagService.delete(1L, 1L));
        verify(tagMapper).deleteById(1L);
        verify(resourceTagMapper).delete(any());
    }

    @Test
    @DisplayName("删除标签 - 无权限")
    void deleteTagNoPermission() {
        Tag tag = new Tag();
        tag.setId(1L);
        tag.setUserId(2L);
        when(tagMapper.selectById(1L)).thenReturn(tag);

        assertThrows(BusinessException.class, () -> tagService.delete(1L, 1L));
    }

    @Test
    @DisplayName("绑定标签 - 正常绑定")
    void bindTag() {
        Tag tag = new Tag();
        tag.setId(1L);
        tag.setUserId(1L);
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
    @DisplayName("绑定标签 - 标签不存在")
    void bindTagNotFound() {
        when(tagMapper.selectById(999L)).thenReturn(null);

        TagBindRequest request = new TagBindRequest();
        request.setTagId(999L);
        request.setResourceType("doc");
        request.setResourceId(100L);

        assertThrows(BusinessException.class, () -> tagService.bind(1L, request));
    }

    @Test
    @DisplayName("绑定标签 - 重复绑定跳过")
    void bindTagDuplicate() {
        Tag tag = new Tag();
        tag.setId(1L);
        tag.setUserId(1L);
        when(tagMapper.selectById(1L)).thenReturn(tag);
        
        ResourceTag existing = new ResourceTag();
        existing.setId(1L);
        when(resourceTagMapper.selectOne(any())).thenReturn(existing);

        TagBindRequest request = new TagBindRequest();
        request.setTagId(1L);
        request.setResourceType("doc");
        request.setResourceId(100L);

        assertDoesNotThrow(() -> tagService.bind(1L, request));
        verify(resourceTagMapper, never()).insert(any());
    }

    @Test
    @DisplayName("查询用户标签列表")
    void listByUserId() {
        Tag t1 = new Tag();
        t1.setId(1L);
        t1.setName("标签1");
        Tag t2 = new Tag();
        t2.setId(2L);
        t2.setName("标签2");
        when(tagMapper.selectList(any())).thenReturn(Arrays.asList(t1, t2));

        List<Tag> result = tagService.listByUserId(1L);

        assertEquals(2, result.size());
    }
}
