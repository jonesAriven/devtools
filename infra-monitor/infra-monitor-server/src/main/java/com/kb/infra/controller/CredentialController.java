package com.kb.infra.controller;

import com.kb.common.exception.NotFoundException;
import com.kb.common.page.PageResult;
import com.kb.common.result.Result;
import com.kb.infra.dto.InfraItemRequest;
import com.kb.infra.entity.InfraItem;
import com.kb.infra.repository.InfraItemRepository;
import com.kb.infra.util.CryptoUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/credentials")
@RequiredArgsConstructor
public class CredentialController {

    private static final String TYPE = "credential";

    private final InfraItemRepository repository;
    private final CryptoUtil cryptoUtil;

    @GetMapping("/list")
    public Result<PageResult<InfraItem>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(page - 1, size,
                Sort.by(Sort.Direction.ASC, "sortOrder")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt")));

        Page<InfraItem> p;
        if (StringUtils.hasText(category)) {
            p = repository.findByTypeAndKeyword(TYPE, 0, "", pageRequest);
            List<InfraItem> filtered = p.getContent().stream()
                    .filter(item -> category.equals(item.getCategory()))
                    .toList();
            return Result.ok(PageResult.of(maskPasswords(filtered), filtered.size(), page, size));
        }
        if (StringUtils.hasText(keyword)) {
            p = repository.findByTypeAndKeyword(TYPE, 0, keyword, pageRequest);
        } else {
            p = repository.findByTypeAndKeyword(TYPE, 0, "", pageRequest);
        }
        return Result.ok(PageResult.of(maskPasswords(p.getContent()), p.getTotalElements(), page, size));
    }

    @GetMapping("/all")
    public Result<List<InfraItem>> all() {
        List<InfraItem> list = repository.findByTypeAndDeletedOrderBySortOrderAscCreatedAtDesc(TYPE, 0);
        return Result.ok(maskPasswords(list));
    }

    @GetMapping("/{id}")
    public Result<InfraItem> getById(@PathVariable String id) {
        InfraItem item = repository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new NotFoundException("凭据不存在: " + id));
        return Result.ok(decryptPassword(item));
    }

    @PostMapping
    public Result<InfraItem> create(@Valid @RequestBody InfraItemRequest request) {
        request.setType(TYPE);
        Map<String, Object> extra = request.getExtra();
        if (extra != null && extra.containsKey("password")) {
            String plain = String.valueOf(extra.get("password"));
            extra.put("passwordEncrypted", cryptoUtil.encrypt(plain));
            extra.remove("password");
        }
        if (extra != null && extra.containsKey("secretKey")) {
            String plain = String.valueOf(extra.get("secretKey"));
            extra.put("secretKeyEncrypted", cryptoUtil.encrypt(plain));
            extra.remove("secretKey");
        }
        request.setExtra(extra);

        InfraItem item = new InfraItem();
        item.setType(TYPE);
        item.setName(request.getName());
        item.setCategory(request.getCategory());
        item.setDescription(request.getDescription());
        item.setExtra(extra != null ? extra : new HashMap<>());
        item.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        item.setDeleted(0);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        InfraItem saved = repository.save(item);
        return Result.ok(maskPassword(saved));
    }

    @PutMapping("/{id}")
    public Result<InfraItem> update(@PathVariable String id, @Valid @RequestBody InfraItemRequest request) {
        InfraItem item = repository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new NotFoundException("凭据不存在: " + id));

        item.setName(request.getName());
        item.setCategory(request.getCategory());
        item.setDescription(request.getDescription());

        if (request.getExtra() != null) {
            Map<String, Object> extra = new HashMap<>(item.getExtra() != null ? item.getExtra() : new HashMap<>());

            if (request.getExtra().containsKey("password")) {
                Object pwd = request.getExtra().get("password");
                if (pwd != null && !String.valueOf(pwd).isEmpty()) {
                    extra.put("passwordEncrypted", cryptoUtil.encrypt(String.valueOf(pwd)));
                }
            }
            if (request.getExtra().containsKey("secretKey")) {
                Object key = request.getExtra().get("secretKey");
                if (key != null && !String.valueOf(key).isEmpty()) {
                    extra.put("secretKeyEncrypted", cryptoUtil.encrypt(String.valueOf(key)));
                }
            }
            for (Map.Entry<String, Object> e : request.getExtra().entrySet()) {
                if (!"password".equals(e.getKey()) && !"secretKey".equals(e.getKey())) {
                    extra.put(e.getKey(), e.getValue());
                }
            }
            item.setExtra(extra);
        }
        if (request.getSortOrder() != null) {
            item.setSortOrder(request.getSortOrder());
        }
        item.setUpdatedAt(LocalDateTime.now());
        InfraItem saved = repository.save(item);
        return Result.ok(maskPassword(saved));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        InfraItem item = repository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new NotFoundException("凭据不存在: " + id));
        item.setDeleted(1);
        item.setUpdatedAt(LocalDateTime.now());
        repository.save(item);
        return Result.ok();
    }

    private List<InfraItem> maskPasswords(List<InfraItem> items) {
        return items.stream().map(this::maskPassword).toList();
    }

    private InfraItem maskPassword(InfraItem item) {
        if (item.getExtra() != null) {
            Map<String, Object> extra = new HashMap<>(item.getExtra());
            if (extra.containsKey("passwordEncrypted")) {
                extra.put("password", "******");
                extra.remove("passwordEncrypted");
            }
            if (extra.containsKey("secretKeyEncrypted")) {
                extra.put("secretKey", "******");
                extra.remove("secretKeyEncrypted");
            }
            item.setExtra(extra);
        }
        return item;
    }

    private InfraItem decryptPassword(InfraItem item) {
        if (item.getExtra() != null) {
            Map<String, Object> extra = new HashMap<>(item.getExtra());
            if (extra.containsKey("passwordEncrypted")) {
                extra.put("password", cryptoUtil.decrypt(String.valueOf(extra.get("passwordEncrypted"))));
                extra.remove("passwordEncrypted");
            }
            if (extra.containsKey("secretKeyEncrypted")) {
                extra.put("secretKey", cryptoUtil.decrypt(String.valueOf(extra.get("secretKeyEncrypted"))));
                extra.remove("secretKeyEncrypted");
            }
            item.setExtra(extra);
        }
        return item;
    }
}
