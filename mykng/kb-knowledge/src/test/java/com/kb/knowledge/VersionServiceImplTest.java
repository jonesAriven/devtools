package com.kb.knowledge;

import com.kb.common.exception.BusinessException;
import com.kb.knowledge.entity.Version;
import com.kb.knowledge.mapper.VersionMapper;
import com.kb.knowledge.mongo.doc.DocContent;
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

    @Test
    @DisplayName("查询版本列表 - 按版本号倒序")
    void listVersions() {
        Version v1 = new Version();
        v1.setId(1L);
        v1.setVersionNum(1);
        Version v2 = new Version();
        v2.setId(2L);
        v2.setVersionNum(2);

        when(versionMapper.selectList(any())).thenReturn(Arrays.asList(v2, v1));

        List<Version> result = versionService.listVersions("doc", 100L);

        assertEquals(2, result.size());
        assertEquals(2, result.get(0).getVersionNum());
    }

    @Test
    @DisplayName("获取单个版本")
    void getVersion() {
        Version v = new Version();
        v.setId(1L);
        v.setVersionNum(1);
        when(versionMapper.selectById(1L)).thenReturn(v);

        Version result = versionService.getVersion(1L);
        assertNotNull(result);
        assertEquals(1, result.getVersionNum());
    }

    @Test
    @DisplayName("获取版本 - 不存在")
    void getVersionNotFound() {
        when(versionMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> versionService.getVersion(999L));
    }

    @Test
    @DisplayName("回滚文档版本 - 切换 isCurrent")
    void rollbackDocVersion() {
        Version version = new Version();
        version.setId(1L);
        version.setResourceType("doc");
        version.setResourceId(100L);
        version.setVersionNum(1);
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
    @DisplayName("回滚 - 不支持的资源类型")
    void rollbackUnsupportedType() {
        Version version = new Version();
        version.setId(1L);
        version.setResourceType("unknown");
        version.setResourceId(100L);
        when(versionMapper.selectById(1L)).thenReturn(version);

        assertThrows(BusinessException.class, () -> versionService.rollback(1L, 1L));
    }
}
