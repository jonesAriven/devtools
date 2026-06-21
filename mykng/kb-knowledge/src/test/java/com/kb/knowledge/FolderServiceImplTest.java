package com.kb.knowledge;

import com.kb.common.exception.BusinessException;
import com.kb.knowledge.dto.folder.FolderCreateRequest;
import com.kb.knowledge.entity.Folder;
import com.kb.knowledge.entity.Space;
import com.kb.knowledge.mapper.FolderMapper;
import com.kb.knowledge.mapper.SpaceMapper;
import com.kb.knowledge.service.EventPublisher;
import com.kb.knowledge.service.SearchIndexService;
import com.kb.knowledge.service.impl.FolderServiceImpl;
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
@DisplayName("文件夹服务单元测试")
class FolderServiceImplTest {

    @Mock private FolderMapper folderMapper;
    @Mock private SpaceMapper spaceMapper;
    @Mock private EventPublisher eventPublisher;
    @Mock private SearchIndexService searchIndexService;

    @InjectMocks
    private FolderServiceImpl folderService;

    @Test
    @DisplayName("获取文件夹树 - 正常构建层级")
    void getTree() {
        Space space = new Space();
        space.setId(1L);
        space.setUserId(1L);
        when(spaceMapper.selectById(1L)).thenReturn(space);

        Folder root = new Folder();
        root.setId(1L);
        root.setParentId(0L);
        root.setName("根文件夹");
        Folder child = new Folder();
        child.setId(2L);
        child.setParentId(1L);
        child.setName("子文件夹");

        when(folderMapper.selectList(any())).thenReturn(Arrays.asList(root, child));

        List<Folder> tree = folderService.getTree(1L, 1L);

        assertEquals(1, tree.size());
        assertEquals("根文件夹", tree.get(0).getName());
        assertNotNull(tree.get(0).getChildren());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("子文件夹", tree.get(0).getChildren().get(0).getName());
    }

    @Test
    @DisplayName("获取文件夹树 - 空间不存在")
    void getTreeSpaceNotFound() {
        when(spaceMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> folderService.getTree(1L, 999L));
    }

    @Test
    @DisplayName("获取文件夹树 - 无权限")
    void getTreeNoPermission() {
        Space space = new Space();
        space.setId(1L);
        space.setUserId(2L);
        when(spaceMapper.selectById(1L)).thenReturn(space);
        assertThrows(BusinessException.class, () -> folderService.getTree(1L, 1L));
    }

    @Test
    @DisplayName("创建文件夹")
    void createFolder() {
        FolderCreateRequest request = new FolderCreateRequest();
        request.setSpaceId(1L);
        request.setParentId(0L);
        request.setName("新文件夹");

        when(folderMapper.insert(any(Folder.class))).thenAnswer(invocation -> {
            Folder f = invocation.getArgument(0);
            f.setId(1L);
            return 1;
        });

        Folder result = folderService.create(1L, request);

        assertNotNull(result);
        assertEquals("新文件夹", result.getName());
        verify(searchIndexService).indexFolder(any(Folder.class));
    }
}
