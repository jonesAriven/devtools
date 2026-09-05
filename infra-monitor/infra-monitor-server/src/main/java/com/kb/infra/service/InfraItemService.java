package com.kb.infra.service;

import com.marschat.common.exception.NotFoundException;
import com.marschat.common.page.PageResult;
import com.kb.infra.dto.InfraItemRequest;
import com.kb.infra.entity.InfraItem;
import com.kb.infra.repository.InfraItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InfraItemService {

    private final InfraItemRepository repository;

    public PageResult<InfraItem> list(String type, String keyword, String category, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page - 1, size,
                Sort.by(Sort.Direction.ASC, "sortOrder")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt")));

        Page<InfraItem> p;
        if (StringUtils.hasText(category) && StringUtils.hasText(keyword)) {
            p = repository.findByTypeAndCategoryAndKeyword(type, category, 0, keyword, pageRequest);
        } else if (StringUtils.hasText(category)) {
            p = repository.findByTypeAndKeyword(type, 0, "", pageRequest);
            List<InfraItem> filtered = p.getContent().stream()
                    .filter(item -> category.equals(item.getCategory()))
                    .toList();
            return PageResult.of(filtered, filtered.size(), page, size);
        } else if (StringUtils.hasText(keyword)) {
            p = repository.findByTypeAndKeyword(type, 0, keyword, pageRequest);
        } else {
            p = repository.findByTypeAndKeyword(type, 0, "", pageRequest);
        }

        return PageResult.of(p.getContent(), p.getTotalElements(), page, size);
    }

    public List<InfraItem> listAll(String type) {
        return repository.findByTypeAndDeletedOrderBySortOrderAscCreatedAtDesc(type, 0);
    }

    public List<InfraItem> listByCategory(String type, String category) {
        return repository.findByTypeAndCategoryAndDeletedOrderBySortOrderAscCreatedAtDesc(type, category, 0);
    }

    public InfraItem getById(String id) {
        return repository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new NotFoundException("基础设施条目不存在: " + id));
    }

    public InfraItem create(InfraItemRequest request) {
        InfraItem item = new InfraItem();
        item.setType(request.getType());
        item.setName(request.getName());
        item.setCategory(request.getCategory());
        item.setDescription(request.getDescription());
        item.setExtra(request.getExtra() != null ? request.getExtra() : new HashMap<>());
        item.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        item.setDeleted(0);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        return repository.save(item);
    }

    public InfraItem update(String id, InfraItemRequest request) {
        InfraItem item = getById(id);
        item.setName(request.getName());
        item.setCategory(request.getCategory());
        item.setDescription(request.getDescription());
        if (request.getExtra() != null) {
            Map<String, Object> merged = new HashMap<>();
            if (item.getExtra() != null) {
                merged.putAll(item.getExtra());
            }
            merged.putAll(request.getExtra());
            item.setExtra(merged);
        }
        if (request.getSortOrder() != null) {
            item.setSortOrder(request.getSortOrder());
        }
        item.setUpdatedAt(LocalDateTime.now());
        return repository.save(item);
    }

    public void delete(String id) {
        InfraItem item = getById(id);
        item.setDeleted(1);
        item.setUpdatedAt(LocalDateTime.now());
        repository.save(item);
    }

    public Map<String, Long> countByType(String type) {
        Map<String, Long> result = new HashMap<>();
        result.put("total", repository.countByTypeAndDeleted(type, 0));
        return result;
    }
}
