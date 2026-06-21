package com.kb.knowledge;

import com.kb.common.exception.BusinessException;
import com.kb.common.page.PageResult;
import com.kb.knowledge.dto.doc.DocCreateRequest;
import com.kb.knowledge.dto.doc.DocUpdateRequest;
import com.kb.knowledge.entity.Doc;
import com.kb.knowledge.entity.Version;
import com.kb.knowledge.mapper.DocMapper;
import com.kb.knowledge.mapper.VersionMapper;
import com.kb.knowledge.mongo.doc.DocContent;
import com.kb.knowledge.mongo.repository.DocContentRepository;
import com.kb.knowledge.service.EventPublisher;
import com.kb.knowledge.service.SearchIndexService;
import com.kb.knowledge.service.impl.DocServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("文档服务单元测试")
class DocServiceImplTest {

    @Mock private DocMapper docMapper;
    @Mock private VersionMapper versionMapper;
    @Mock private DocContentRepository docContentRepository;
    @Mock private EventPublisher eventPublisher;
    @Mock private SearchIndexService searchIndexService;

    @InjectMocks
    private DocServiceImpl docService;

    @Test
    @DisplayName("创建文档 - 同时创建内容+版本+索引")
    void createDoc() {
        DocCreateRequest request = new DocCreateRequest();
        request.setTitle("测试文档");
        request.setContent("内容");
        request.setFolderId(0L);

        when(docMapper.insert(any(Doc.class))).thenAnswer(invocation -> {
            Doc d = invocation.getArgument(0);
            d.setId(1L);
            return 1;
        });

        Doc result = docService.create(1L, request);

        assertNotNull(result);
        assertEquals("测试文档", result.getTitle());
        verify(docContentRepository).save(any(DocContent.class));
        verify(versionMapper).insert(any(Version.class));
        verify(searchIndexService).indexDoc(any(Doc.class), eq("内容"));
    }

    @Test
    @DisplayName("编辑文档 - 更新内容+创建新版本")
    void updateDoc() {
        DocUpdateRequest request = new DocUpdateRequest();
        request.setTitle("更新标题");
        request.setContent("新内容");

        Doc doc = new Doc();
        doc.setId(1L);
        doc.setUserId(1L);
        when(docMapper.selectById(1L)).thenReturn(doc);
        
        DocContent content = new DocContent();
        content.setDocId(1L);
        content.setVersion(1);
        content.setIsCurrent(true);
        when(docContentRepository.findByDocIdAndIsCurrentTrue(1L)).thenReturn(Optional.of(content));
        when(docContentRepository.findByDocIdOrderByVersionDesc(1L)).thenReturn(Arrays.asList(content));

        assertDoesNotThrow(() -> docService.update(1L, 1L, request));
        verify(docMapper).updateById(any(Doc.class));
        verify(docContentRepository, times(2)).save(any(DocContent.class));
    }

    @Test
    @DisplayName("删除文档 - 软删除+清除索引")
    void deleteDoc() {
        Doc doc = new Doc();
        doc.setId(1L);
        doc.setUserId(1L);
        when(docMapper.selectById(1L)).thenReturn(doc);

        assertDoesNotThrow(() -> docService.delete(1L, 1L));
        verify(docMapper).deleteById(1L);
        verify(searchIndexService).removeDocIndex(1L);
    }

    @Test
    @DisplayName("删除文档 - 无权限")
    void deleteDocNoPermission() {
        Doc doc = new Doc();
        doc.setId(1L);
        doc.setUserId(2L);
        when(docMapper.selectById(1L)).thenReturn(doc);

        assertThrows(BusinessException.class, () -> docService.delete(1L, 1L));
    }

    @Test
    @DisplayName("收藏/取消收藏文档")
    void toggleStar() {
        Doc doc = new Doc();
        doc.setId(1L);
        doc.setUserId(1L);
        doc.setStarred(0);
        when(docMapper.selectById(1L)).thenReturn(doc);

        assertDoesNotThrow(() -> docService.star(1L, 1L));
        verify(docMapper).updateById(any(Doc.class));
    }
}
