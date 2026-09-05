package com.kb.file;

import com.marschat.common.exception.BusinessException;
import com.kb.file.entity.Bucket;
import com.kb.file.mapper.BucketMapper;
import com.kb.file.mapper.KbFileMapper;
import com.kb.file.service.impl.BucketServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Bucket 服务单元测试")
class BucketServiceImplTest {

    @Mock private BucketMapper bucketMapper;
    @Mock private KbFileMapper kbFileMapper;

    @InjectMocks
    private BucketServiceImpl bucketService;

    @Test
    @DisplayName("列表查询 - 返回全部 Bucket 按ID升序")
    void list_returnsAllBuckets() {
        Bucket b1 = new Bucket();
        b1.setId(1L);
        b1.setName("kb-file");
        Bucket b2 = new Bucket();
        b2.setId(2L);
        b2.setName("kb-backup");

        when(bucketMapper.selectList(any())).thenReturn(Arrays.asList(b1, b2));

        List<Bucket> result = bucketService.list();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("kb-file", result.get(0).getName());
    }

    @Test
    @DisplayName("获取统计 - 返回 Bucket 信息和文件数量")
    void getStats_success_returnsStats() {
        Bucket bucket = new Bucket();
        bucket.setId(1L);
        bucket.setName("kb-file");
        when(bucketMapper.selectById(1L)).thenReturn(bucket);
        when(kbFileMapper.selectCount(any())).thenReturn(42L);

        Map<String, Object> stats = bucketService.getStats(1L);

        assertNotNull(stats);
        assertSame(bucket, stats.get("bucket"));
        assertEquals(42L, stats.get("fileCount"));
    }

    @Test
    @DisplayName("获取统计 - Bucket 不存在抛出异常")
    void getStats_bucketNotFound() {
        when(bucketMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> bucketService.getStats(999L));
        assertEquals(404, ex.getCode());
    }
}
