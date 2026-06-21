package com.kb.knowledge;

import com.kb.common.page.PageResult;
import com.kb.knowledge.dto.share.ShareCreateRequest;
import com.kb.knowledge.entity.Share;
import com.kb.knowledge.mapper.ShareMapper;
import com.kb.knowledge.service.EventPublisher;
import com.kb.knowledge.service.impl.ShareServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("分享服务单元测试")
class ShareServiceImplTest {

    @Mock private ShareMapper shareMapper;
    @Mock private EventPublisher eventPublisher;

    @InjectMocks
    private ShareServiceImpl shareService;

    @Test
    @DisplayName("创建分享 - 自动生成 code 和 extractCode")
    void createShare() {
        ShareCreateRequest request = new ShareCreateRequest();
        request.setResourceType("doc");
        request.setResourceId(100L);

        when(shareMapper.insert(any(Share.class))).thenAnswer(invocation -> {
            Share s = invocation.getArgument(0);
            s.setId(1L);
            return 1;
        });

        Share result = shareService.create(1L, request);

        assertNotNull(result);
        assertNotNull(result.getCode());
        assertNotNull(result.getExtractCode());
        assertEquals(0, result.getViewCount());
        verify(eventPublisher).publishKnowledgeEvent(eq(1L), eq("SHARE"), any(), any(), any());
    }

    @Test
    @DisplayName("创建分享 - 自定义提取码")
    void createShareWithCustomExtractCode() {
        ShareCreateRequest request = new ShareCreateRequest();
        request.setResourceType("doc");
        request.setResourceId(100L);
        request.setExtractCode("1234");

        when(shareMapper.insert(any(Share.class))).thenAnswer(invocation -> {
            Share s = invocation.getArgument(0);
            s.setId(1L);
            return 1;
        });

        Share result = shareService.create(1L, request);
        assertEquals("1234", result.getExtractCode());
    }
}
