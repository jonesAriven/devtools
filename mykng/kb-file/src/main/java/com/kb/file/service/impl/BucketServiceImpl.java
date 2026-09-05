package com.kb.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marschat.common.exception.BusinessException;
import com.kb.file.entity.Bucket;
import com.kb.file.entity.KbFile;
import com.kb.file.mapper.BucketMapper;
import com.kb.file.mapper.KbFileMapper;
import com.kb.file.service.BucketService;
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
            throw new BusinessException(404, "Bucket不存在");
        }

        long fileCount = kbFileMapper.selectCount(
                new LambdaQueryWrapper<KbFile>().likeRight(KbFile::getMinioPath, bucket.getName() + "/"));

        Map<String, Object> stats = new HashMap<>();
        stats.put("bucket", bucket);
        stats.put("fileCount", fileCount);
        return stats;
    }
}
