package com.kb.file.controller;

import com.marschat.common.result.Result;
import com.kb.file.entity.Bucket;
import com.kb.file.service.BucketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/bucket")
@RequiredArgsConstructor
public class BucketController {

    private final BucketService bucketService;

    @GetMapping("/list")
    public Result<List<Bucket>> list() {
        return Result.ok(bucketService.list());
    }

    @GetMapping("/{id}/stats")
    public Result<Map<String, Object>> getStats(@PathVariable Long id) {
        return Result.ok(bucketService.getStats(id));
    }
}
