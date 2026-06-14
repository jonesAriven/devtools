package com.jones.kb.controller;

import com.jones.kb.common.R;
import com.jones.kb.entity.Bucket;
import com.jones.kb.service.BucketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bucket")
@RequiredArgsConstructor
public class BucketController {

    private final BucketService bucketService;

    @GetMapping("/list")
    public R<List<Bucket>> list() {
        return R.ok(bucketService.list());
    }

    @GetMapping("/{id}/stats")
    public R<Map<String, Object>> getStats(@PathVariable Long id) {
        return R.ok(bucketService.getStats(id));
    }
}
