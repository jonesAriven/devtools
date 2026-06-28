package com.kb.knowledge;

import com.kb.common.exception.BusinessException;
import com.kb.knowledge.entity.Version;
import com.kb.knowledge.mapper.VersionMapper;
import com.kb.knowledge.mongo.doc.DocContent;
import com.kb.knowledge.mongo.doc.WebContent;
import com.kb.knowledge.mongo.repository.DocContentRepository;
import com.kb.knowledge.mongo.repository.WebContentRepository;
import com.kb.knowledge.service.impl.VersionServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
@DisplayName("版本控制服务单元测试")
class VersionServiceImplTest {

    @Mock private VersionMapper versionMapper;
    @Mock private DocContentRepository docContentRepository;
    @Mock private WebContentRepository webContentRepository;

    @InjectMocks
    private VersionServiceImpl versionService;

    private Version buildVersion(Long id, String type, Long resourceId, int versionNum) {
        Version v = new Version();
        v.setId(id);
        v.setResourceType(type);
        v.setResourceId(resourceId);
        v.setVersionNum(versionNum);
        return v;
    }

    @Test
    @DisplayName("listVersions - 按版本号倒序返回")
    void listVersionsNormal() {
        Version v1 = buildVersion(1L, "doc", 100L, 1);
        Version v2 = buildVersion(2L, "doc", 100L, 2);
        when(versionMapper.selectList(any())).thenReturn(Arrays.asList(v2, v1));

        List<Version> result = versionService.listVersions("doc", 100L);
        assertEquals(2, result.size());
        assertEquals(2, result.get(0).getVersionNum());
    }

    @Test
    @DisplayName("listVersions - 空列表")
    void listVersionsEmpty() {
        when(versionMapper.selectList(any())).thenReturn(Collections.emptyList());
        List<Version> result = versionService.listVersions("doc", 100L);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getVersion - 正常")
    void getVersionNormal() {
        Version v = buildVersion(1L, "doc", 100L, 1);
        when(versionMapper.selectById(1L)).thenReturn(v);

        Version result = versionService.getVersion(1L);
        assertNotNull(result);
        assertEquals(1, result.getVersionNum());
    }

    @Test
    @DisplayName("getVersion - 不存在抛异常")
    void getVersionNotFound() {
        when(versionMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> versionService.getVersion(999L));
    }

    @Test
    @DisplayName("rollback - 文件类型由 kb-file 处理（仅日志）")
    void rollbackFile() {
        Version version = buildVersion(1L, "file", 100L, 1);
        when(versionMapper.selectById(1L)).thenReturn(version);

        Version result = versionService.rollback(1L, 1L);
        assertNotNull(result);
        assertEquals("file", result.getResourceType());
        // 文件回滚不调用 mongo 仓储
        verifyNoInteractions(docContentRepository);
        verifyNoInteractions(webContentRepository);
    }

    @Test
    @DisplayName("rollback - 文档回滚正常切换 isCurrent")
    void rollbackDocNormal() {
        Version version = buildVersion(1L, "doc", 100L, 1);
        when(versionMapper.selectById(1L)).thenReturn(version);

        DocContent current = new DocContent();
        current.setDocId(100L);
        current.setVersion(2);
        current.setIsCurrent(true);
        when(docContentRepository.findByDocIdAndIsCurrentTrue(100L)).thenReturn(Optional.of(current));

        DocContent target = new DocContent();
        target.setDocId(100L);
        target.setVersion(1);
        when(docContentRepository.findByDocIdOrderByVersionDesc(100L))
                .thenReturn(Arrays.asList(current, target));

        Version result = versionService.rollback(1L, 1L);
        assertNotNull(result);
        verify(docContentRepository, atLeast(2)).save(any(DocContent.class));
    }

    @Test
    @DisplayName("rollback - 文档无当前版本时仍调用（ifPresent 不执行）")
    void rollbackDocNoCurrent() {
        Version version = buildVersion(1L, "doc", 100L, 1);
        when(versionMapper.selectById(1L)).thenReturn(version);

        when(docContentRepository.findByDocIdAndIsCurrentTrue(100L)).thenReturn(Optional.empty());
        // 目标版本不存在
        DocContent other = new DocContent();
        other.setDocId(100L);
        other.setVersion(5);
        when(docContentRepository.findByDocIdOrderByVersionDesc(100L))
                .thenReturn(Collections.singletonList(other));

        Version result = versionService.rollback(1L, 1L);
        assertNotNull(result);
        // 没有 current，不会调用 save(current)
        verify(docContentRepository, never()).save(any(DocContent.class));
    }

    @Test
    @DisplayName("rollback - 网页回滚正常切换 isCurrent")
    void rollbackWebNormal() {
        Version version = buildVersion(1L, "web", 100L, 1);
        when(versionMapper.selectById(1L)).thenReturn(version);

        WebContent current = new WebContent();
        current.setWebId(100L);
        current.setVersion(2);
        current.setIsCurrent(true);
        when(webContentRepository.findByWebIdAndIsCurrentTrue(100L)).thenReturn(Optional.of(current));

        WebContent target = new WebContent();
        target.setWebId(100L);
        target.setVersion(1);
        when(webContentRepository.findByWebIdOrderByVersionDesc(100L))
                .thenReturn(Arrays.asList(current, target));

        Version result = versionService.rollback(1L, 1L);
        assertNotNull(result);
        verify(webContentRepository, atLeast(2)).save(any(WebContent.class));
    }

    @Test
    @DisplayName("rollback - 网页无当前版本且无目标版本")
    void rollbackWebNoCurrentNoTarget() {
        Version version = buildVersion(1L, "web", 100L, 1);
        when(versionMapper.selectById(1L)).thenReturn(version);

        when(webContentRepository.findByWebIdAndIsCurrentTrue(100L)).thenReturn(Optional.empty());
        WebContent other = new WebContent();
        other.setWebId(100L);
        other.setVersion(5);
        when(webContentRepository.findByWebIdOrderByVersionDesc(100L))
                .thenReturn(Collections.singletonList(other));

        Version result = versionService.rollback(1L, 1L);
        assertNotNull(result);
        verify(webContentRepository, never()).save(any(WebContent.class));
    }

    @Test
    @DisplayName("rollback - 不支持的资源类型")
    void rollbackUnsupportedType() {
        Version version = buildVersion(1L, "unknown", 100L, 1);
        when(versionMapper.selectById(1L)).thenReturn(version);

        assertThrows(BusinessException.class, () -> versionService.rollback(1L, 1L));
    }

    @Test
    @DisplayName("rollback - 版本不存在")
    void rollbackVersionNotFound() {
        when(versionMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> versionService.rollback(1L, 1L));
    }
}
