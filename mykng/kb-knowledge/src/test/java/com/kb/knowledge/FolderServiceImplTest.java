package com.kb.knowledge;

import com.marschat.common.exception.BusinessException;
import com.kb.knowledge.dto.folder.FolderCreateRequest;
import com.kb.knowledge.dto.folder.FolderMoveRequest;
import com.kb.knowledge.dto.folder.FolderSortRequest;
import com.kb.knowledge.entity.Doc;
import com.kb.knowledge.entity.Folder;
import com.kb.knowledge.entity.Space;
import com.kb.knowledge.entity.WebPage;
import com.kb.knowledge.feign.FileClient;
import com.kb.knowledge.mapper.DocMapper;
import com.kb.knowledge.mapper.FolderMapper;
import com.kb.knowledge.mapper.SpaceMapper;
import com.kb.knowledge.mapper.WebPageMapper;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("文件夹服务单元测试")
class FolderServiceImplTest {

    @Mock private FolderMapper folderMapper;
    @Mock private SpaceMapper spaceMapper;
    @Mock private DocMapper docMapper;
    @Mock private WebPageMapper webPageMapper;
    @Mock private EventPublisher eventPublisher;
    @Mock private SearchIndexService searchIndexService;
    @Mock private FileClient fileClient;

    @InjectMocks
    private FolderServiceImpl folderService;

    private Space ownerSpace(Long spaceId, Long userId) {
        Space space = new Space();
        space.setId(spaceId);
        space.setUserId(userId);
        return space;
    }

    @Test
    @DisplayName("获取文件夹树 - 正常构建层级")
    void getTree() {
        Space space = ownerSpace(1L, 1L);
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
        assertThrows(BusinessException.class, () -> folderService.getTree(999L, 1L));
    }

    @Test
    @DisplayName("获取文件夹树 - 无权限")
    void getTreeNoPermission() {
        Space space = ownerSpace(1L, 2L);
        when(spaceMapper.selectById(1L)).thenReturn(space);
        assertThrows(BusinessException.class, () -> folderService.getTree(1L, 999L));
    }

    @Test
    @DisplayName("获取文件夹树 - 空列表")
    void getTreeEmpty() {
        Space space = ownerSpace(1L, 1L);
        when(spaceMapper.selectById(1L)).thenReturn(space);
        when(folderMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<Folder> tree = folderService.getTree(1L, 1L);
        assertNotNull(tree);
        assertTrue(tree.isEmpty());
    }

    @Test
    @DisplayName("获取文件夹树 - 父ID为 null 时归到根")
    void getTreeNullParentTreatedAsRoot() {
        Space space = ownerSpace(1L, 1L);
        when(spaceMapper.selectById(1L)).thenReturn(space);

        Folder folder = new Folder();
        folder.setId(10L);
        folder.setParentId(null);
        folder.setName("null父文件夹");

        when(folderMapper.selectList(any())).thenReturn(Collections.singletonList(folder));

        List<Folder> tree = folderService.getTree(1L, 1L);
        assertEquals(1, tree.size());
        assertEquals("null父文件夹", tree.get(0).getName());
    }

    @Test
    @DisplayName("创建文件夹 - 正常")
    void createFolder() {
        Space space = ownerSpace(1L, 1L);
        when(spaceMapper.selectById(1L)).thenReturn(space);

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
        assertEquals(0L, result.getParentId());
        verify(searchIndexService).indexFolder(any(Folder.class));
        verify(eventPublisher).publishKnowledgeEvent(eq(1L), eq("CREATE"), eq("folder"), any(), anyString());
    }

    @Test
    @DisplayName("创建文件夹 - parentId 为 null 默认 0")
    void createFolderNullParentId() {
        Space space = ownerSpace(1L, 1L);
        when(spaceMapper.selectById(1L)).thenReturn(space);

        FolderCreateRequest request = new FolderCreateRequest();
        request.setSpaceId(1L);
        request.setParentId(null);
        request.setName("根");
        request.setSortOrder(5);

        when(folderMapper.insert(any(Folder.class))).thenAnswer(invocation -> {
            Folder f = invocation.getArgument(0);
            f.setId(2L);
            return 1;
        });

        Folder result = folderService.create(1L, request);
        assertEquals(0L, result.getParentId());
        assertEquals(5, result.getSortOrder());
    }

    @Test
    @DisplayName("创建文件夹 - sortOrder 为 null 默认 0")
    void createFolderNullSortOrder() {
        Space space = ownerSpace(1L, 1L);
        when(spaceMapper.selectById(1L)).thenReturn(space);

        FolderCreateRequest request = new FolderCreateRequest();
        request.setSpaceId(1L);
        request.setParentId(0L);
        request.setName("根");
        request.setSortOrder(null);

        when(folderMapper.insert(any(Folder.class))).thenAnswer(invocation -> {
            Folder f = invocation.getArgument(0);
            f.setId(2L);
            return 1;
        });

        Folder result = folderService.create(1L, request);
        assertEquals(0, result.getSortOrder());
    }

    @Test
    @DisplayName("创建文件夹 - 空间无权限")
    void createFolderNoPermission() {
        Space space = ownerSpace(1L, 2L);
        when(spaceMapper.selectById(1L)).thenReturn(space);

        FolderCreateRequest request = new FolderCreateRequest();
        request.setSpaceId(1L);
        request.setName("test");

        assertThrows(BusinessException.class, () -> folderService.create(1L, request));
    }

    @Test
    @DisplayName("创建文件夹 - 空间不存在")
    void createFolderSpaceNotFound() {
        when(spaceMapper.selectById(1L)).thenReturn(null);

        FolderCreateRequest request = new FolderCreateRequest();
        request.setSpaceId(1L);
        request.setName("test");

        assertThrows(BusinessException.class, () -> folderService.create(1L, request));
    }

    @Test
    @DisplayName("getById - 正常")
    void getByIdNormal() {
        Space space = ownerSpace(1L, 1L);
        when(spaceMapper.selectById(1L)).thenReturn(space);

        Folder folder = new Folder();
        folder.setId(10L);
        folder.setSpaceId(1L);
        folder.setName("F");
        when(folderMapper.selectById(10L)).thenReturn(folder);

        Folder result = folderService.getById(10L, 1L);
        assertEquals("F", result.getName());
    }

    @Test
    @DisplayName("getById - 文件夹不存在")
    void getByIdNotFound() {
        when(folderMapper.selectById(10L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> folderService.getById(10L, 1L));
    }

    @Test
    @DisplayName("getById - 无权限")
    void getByIdNoPermission() {
        Space space = ownerSpace(1L, 2L);
        when(spaceMapper.selectById(1L)).thenReturn(space);

        Folder folder = new Folder();
        folder.setId(10L);
        folder.setSpaceId(1L);
        when(folderMapper.selectById(10L)).thenReturn(folder);

        assertThrows(BusinessException.class, () -> folderService.getById(10L, 999L));
    }

    @Test
    @DisplayName("update - 正常")
    void updateNormal() {
        Space space = ownerSpace(1L, 1L);
        when(spaceMapper.selectById(1L)).thenReturn(space);

        Folder folder = new Folder();
        folder.setId(10L);
        folder.setSpaceId(1L);
        when(folderMapper.selectById(10L)).thenReturn(folder);

        Folder result = folderService.update(10L, 1L, "新名称");
        assertEquals("新名称", result.getName());
        verify(folderMapper).updateById(any(Folder.class));
        verify(searchIndexService).indexFolder(any(Folder.class));
    }

    @Test
    @DisplayName("update - 文件夹不存在")
    void updateNotFound() {
        when(folderMapper.selectById(10L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> folderService.update(10L, 1L, "新名称"));
    }

    @Test
    @DisplayName("update - 无权限")
    void updateNoPermission() {
        Space space = ownerSpace(1L, 2L);
        when(spaceMapper.selectById(1L)).thenReturn(space);

        Folder folder = new Folder();
        folder.setId(10L);
        folder.setSpaceId(1L);
        when(folderMapper.selectById(10L)).thenReturn(folder);

        assertThrows(BusinessException.class, () -> folderService.update(10L, 999L, "新名称"));
    }

    @Test
    @DisplayName("delete - 正常")
    void deleteNormal() {
        Space space = ownerSpace(1L, 1L);
        when(spaceMapper.selectById(1L)).thenReturn(space);

        Folder folder = new Folder();
        folder.setId(10L);
        folder.setSpaceId(1L);
        folder.setName("F");
        when(folderMapper.selectById(10L)).thenReturn(folder);
        when(folderMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(docMapper.selectCount(any())).thenReturn(0L);
        when(webPageMapper.selectCount(any())).thenReturn(0L);

        assertDoesNotThrow(() -> folderService.delete(10L, 1L));
        verify(folderMapper).deleteById(10L);
        verify(searchIndexService).removeFolderIndex(10L);
        verify(eventPublisher).publishKnowledgeEvent(eq(1L), eq("DELETE"), eq("folder"), eq(10L), anyString());
    }

    @Test
    @DisplayName("delete - 文件夹不存在")
    void deleteNotFound() {
        when(folderMapper.selectById(10L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> folderService.delete(10L, 1L));
    }

    @Test
    @DisplayName("delete - 无权限")
    void deleteNoPermission() {
        Space space = ownerSpace(1L, 2L);
        when(spaceMapper.selectById(1L)).thenReturn(space);

        Folder folder = new Folder();
        folder.setId(10L);
        folder.setSpaceId(1L);
        when(folderMapper.selectById(10L)).thenReturn(folder);

        assertThrows(BusinessException.class, () -> folderService.delete(10L, 999L));
    }

    @Test
    @DisplayName("delete - 存在子文件夹不允许删除")
    void deleteHasChildren() {
        Space space = ownerSpace(1L, 1L);
        when(spaceMapper.selectById(1L)).thenReturn(space);

        Folder folder = new Folder();
        folder.setId(10L);
        folder.setSpaceId(1L);
        when(folderMapper.selectById(10L)).thenReturn(folder);

        Folder child = new Folder();
        child.setId(11L);
        when(folderMapper.selectList(any())).thenReturn(Collections.singletonList(child));

        BusinessException ex = assertThrows(BusinessException.class, () -> folderService.delete(10L, 1L));
        assertTrue(ex.getMessage().contains("子文件夹"));
    }

    @Test
    @DisplayName("delete - 存在文档不允许删除")
    void deleteHasDocs() {
        Space space = ownerSpace(1L, 1L);
        when(spaceMapper.selectById(1L)).thenReturn(space);

        Folder folder = new Folder();
        folder.setId(10L);
        folder.setSpaceId(1L);
        when(folderMapper.selectById(10L)).thenReturn(folder);
        when(folderMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(docMapper.selectCount(any())).thenReturn(5L);

        BusinessException ex = assertThrows(BusinessException.class, () -> folderService.delete(10L, 1L));
        assertTrue(ex.getMessage().contains("文档"));
    }

    @Test
    @DisplayName("delete - 存在网页收藏不允许删除")
    void deleteHasWebPages() {
        Space space = ownerSpace(1L, 1L);
        when(spaceMapper.selectById(1L)).thenReturn(space);

        Folder folder = new Folder();
        folder.setId(10L);
        folder.setSpaceId(1L);
        when(folderMapper.selectById(10L)).thenReturn(folder);
        when(folderMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(docMapper.selectCount(any())).thenReturn(0L);
        when(webPageMapper.selectCount(any())).thenReturn(3L);

        BusinessException ex = assertThrows(BusinessException.class, () -> folderService.delete(10L, 1L));
        assertTrue(ex.getMessage().contains("网页"));
    }

    @Test
    @DisplayName("move - 正常移到根")
    void moveToRoot() {
        Space space = ownerSpace(1L, 1L);
        when(spaceMapper.selectById(1L)).thenReturn(space);

        Folder folder = new Folder();
        folder.setId(10L);
        folder.setSpaceId(1L);
        when(folderMapper.selectById(10L)).thenReturn(folder);

        FolderMoveRequest request = new FolderMoveRequest();
        request.setParentId(0L);

        assertDoesNotThrow(() -> folderService.move(10L, 1L, request));
        verify(folderMapper).updateById(any(Folder.class));
        verify(searchIndexService).indexFolder(any(Folder.class));
    }

    @Test
    @DisplayName("move - parentId 为 null")
    void moveNullParentId() {
        Space space = ownerSpace(1L, 1L);
        when(spaceMapper.selectById(1L)).thenReturn(space);

        Folder folder = new Folder();
        folder.setId(10L);
        folder.setSpaceId(1L);
        when(folderMapper.selectById(10L)).thenReturn(folder);

        FolderMoveRequest request = new FolderMoveRequest();
        request.setParentId(null);

        assertDoesNotThrow(() -> folderService.move(10L, 1L, request));
        verify(folderMapper).updateById(any(Folder.class));
    }

    @Test
    @DisplayName("move - 目标目录不存在")
    void moveTargetNotFound() {
        Space space = ownerSpace(1L, 1L);
        when(spaceMapper.selectById(1L)).thenReturn(space);

        Folder folder = new Folder();
        folder.setId(10L);
        folder.setSpaceId(1L);
        when(folderMapper.selectById(10L)).thenReturn(folder);
        when(folderMapper.selectById(99L)).thenReturn(null);

        FolderMoveRequest request = new FolderMoveRequest();
        request.setParentId(99L);

        BusinessException ex = assertThrows(BusinessException.class, () -> folderService.move(10L, 1L, request));
        assertTrue(ex.getMessage().contains("目标目录"));
    }

    @Test
    @DisplayName("move - 不能移到自身子目录下")
    void moveToIntolerableChild() {
        Space space = ownerSpace(1L, 1L);
        when(spaceMapper.selectById(1L)).thenReturn(space);

        Folder folder = new Folder();
        folder.setId(10L);
        folder.setSpaceId(1L);
        when(folderMapper.selectById(10L)).thenReturn(folder);

        // 目标父目录为 20，20 的父为 10（即 10 的子目录）
        Folder target = new Folder();
        target.setId(20L);
        target.setParentId(10L);
        when(folderMapper.selectById(20L)).thenReturn(target);

        FolderMoveRequest request = new FolderMoveRequest();
        request.setParentId(20L);

        BusinessException ex = assertThrows(BusinessException.class, () -> folderService.move(10L, 1L, request));
        assertTrue(ex.getMessage().contains("子目录"));
    }

    @Test
    @DisplayName("move - 文件夹不存在")
    void moveNotFound() {
        when(folderMapper.selectById(10L)).thenReturn(null);

        FolderMoveRequest request = new FolderMoveRequest();
        request.setParentId(0L);

        assertThrows(BusinessException.class, () -> folderService.move(10L, 1L, request));
    }

    @Test
    @DisplayName("move - 无权限")
    void moveNoPermission() {
        Space space = ownerSpace(1L, 2L);
        when(spaceMapper.selectById(1L)).thenReturn(space);

        Folder folder = new Folder();
        folder.setId(10L);
        folder.setSpaceId(1L);
        when(folderMapper.selectById(10L)).thenReturn(folder);

        FolderMoveRequest request = new FolderMoveRequest();
        request.setParentId(0L);

        assertThrows(BusinessException.class, () -> folderService.move(10L, 999L, request));
    }

    @Test
    @DisplayName("sort - 正常")
    void sortNormal() {
        Space space = ownerSpace(1L, 1L);
        when(spaceMapper.selectById(1L)).thenReturn(space);

        Folder folder = new Folder();
        folder.setId(10L);
        folder.setSpaceId(1L);
        when(folderMapper.selectById(10L)).thenReturn(folder);

        FolderSortRequest request = new FolderSortRequest();
        request.setSortOrder(99);

        assertDoesNotThrow(() -> folderService.sort(10L, 1L, request));
        verify(folderMapper).updateById(any(Folder.class));
        verify(searchIndexService).indexFolder(any(Folder.class));
    }

    @Test
    @DisplayName("sort - 文件夹不存在")
    void sortNotFound() {
        when(folderMapper.selectById(10L)).thenReturn(null);

        FolderSortRequest request = new FolderSortRequest();
        request.setSortOrder(1);

        assertThrows(BusinessException.class, () -> folderService.sort(10L, 1L, request));
    }

    @Test
    @DisplayName("sort - 无权限")
    void sortNoPermission() {
        Space space = ownerSpace(1L, 2L);
        when(spaceMapper.selectById(1L)).thenReturn(space);

        Folder folder = new Folder();
        folder.setId(10L);
        folder.setSpaceId(1L);
        when(folderMapper.selectById(10L)).thenReturn(folder);

        FolderSortRequest request = new FolderSortRequest();
        request.setSortOrder(1);

        assertThrows(BusinessException.class, () -> folderService.sort(10L, 999L, request));
    }
}
