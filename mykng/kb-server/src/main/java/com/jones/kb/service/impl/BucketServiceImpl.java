package com.jones.kb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jones.kb.entity.Bucket;
import com.jones.kb.entity.KbFile;
import com.jones.kb.mapper.BucketMapper;
import com.jones.kb.mapper.KbFileMapper;
import com.jones.kb.service.BucketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BucketServiceImpl implements BucketService {

    private final BucketMapper bucketMapper;
    private final KbFileMapper kbFileMapper;

    @Override
    public List<Bucket> list() {
        return bucketMapper.selectList(
                new LambdaQueryWrapper<Bucket>().orderByAsc(Bucket::getId));
    }

    @Override
    public Map<String, Object> getStats(Long id) {
        Bucket bucket = bucketMapper.selectById(id);
        if (bucket == null) {
            throw new com.jones.kb.common.BusinessException("Bucket不存在");
        }

        long fileCount = kbFileMapper.selectCount(
                new LambdaQueryWrapper<KbFile>().likeRight(KbFile::getMinioPath, bucket.getName() + "/"));

        Map<String, Object> stats = new HashMap<>();
        stats.put("bucket", bucket);
        stats.put("fileCount", fileCount);
        return stats;
    }
}
