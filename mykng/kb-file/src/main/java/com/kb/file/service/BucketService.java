package com.kb.file.service;

import com.kb.file.entity.Bucket;

import java.util.List;
import java.util.Map;

public interface BucketService {

    List<Bucket> list();

    Map<String, Object> getStats(Long id);
}
