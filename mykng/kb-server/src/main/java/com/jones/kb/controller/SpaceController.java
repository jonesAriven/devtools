package com.jones.kb.controller;

import com.jones.kb.common.R;
import com.jones.kb.dto.space.SpaceCreateRequest;
import com.jones.kb.dto.space.SpaceUpdateRequest;
import com.jones.kb.entity.Space;
import com.jones.kb.service.SpaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/space")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;

    @GetMapping("/list")
    public R<List<Space>> list() {
        return R.ok(spaceService.listByUserId(getCurrentUserId()));
    }

    @PostMapping
    public R<Space> create(@Valid @RequestBody SpaceCreateRequest request) {
        return R.ok(spaceService.create(getCurrentUserId(), request));
    }

    @PutMapping("/{id}")
    public R<Space> update(@PathVariable Long id, @RequestBody SpaceUpdateRequest request) {
        return R.ok(spaceService.update(id, getCurrentUserId(), request));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        spaceService.delete(id, getCurrentUserId());
        return R.ok();
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}
