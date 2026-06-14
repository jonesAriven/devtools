package com.jones.kb.service;

import com.jones.kb.entity.Bucket;

import java.util.List;
import java.util.Map;

public interface BucketService {

    List<Bucket> list();

    Map<String, Object> getStats(Long id);
}
