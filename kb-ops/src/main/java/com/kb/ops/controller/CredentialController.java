package com.kb.ops.controller;

import com.marschat.common.page.PageResult;
import com.marschat.common.result.Result;
import com.kb.ops.dto.CredentialRequest;
import com.kb.ops.entity.Credential;
import com.kb.ops.service.CredentialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ops/credential")
@RequiredArgsConstructor
public class CredentialController {

    private final CredentialService credentialService;

    @GetMapping("/list")
    public Result<PageResult<Credential>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(credentialService.list(type, keyword, page, size));
    }

    @GetMapping("/{id}")
    public Result<Credential> getById(@PathVariable Long id,
                                      @RequestParam(defaultValue = "false") boolean revealPassword) {
        return Result.ok(credentialService.getById(id, revealPassword));
    }

    @PostMapping
    public Result<Credential> create(@Valid @RequestBody CredentialRequest request) {
        return Result.ok(credentialService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Credential> update(@PathVariable Long id, @Valid @RequestBody CredentialRequest request) {
        return Result.ok(credentialService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        credentialService.delete(id);
        return Result.ok();
    }
}
