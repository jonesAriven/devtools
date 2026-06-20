package com.kb.knowledge.controller;

import com.kb.common.result.Result;
import com.kb.knowledge.dto.space.SpaceCreateRequest;
import com.kb.knowledge.dto.space.SpaceUpdateRequest;
import com.kb.knowledge.entity.Space;
import com.kb.knowledge.service.SpaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/space")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;

    @GetMapping("/list")
    public Result<List<Space>> list() {
        return Result.ok(spaceService.listByUserId(getCurrentUserId()));
    }

    @PostMapping
    public Result<Space> create(@Valid @RequestBody SpaceCreateRequest request) {
        return Result.ok(spaceService.create(getCurrentUserId(), request));
    }

    @PutMapping("/{id}")
    public Result<Space> update(@PathVariable Long id, @RequestBody SpaceUpdateRequest request) {
        return Result.ok(spaceService.update(id, getCurrentUserId(), request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        spaceService.delete(id, getCurrentUserId());
        return Result.ok();
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}
