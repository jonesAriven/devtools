package com.kb.ops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kb.common.exception.NotFoundException;
import com.kb.common.page.PageResult;
import com.kb.ops.dto.OpsKnowledgeRequest;
import com.kb.ops.entity.OpsKnowledge;
import com.kb.ops.mapper.OpsKnowledgeMapper;
import com.kb.ops.service.OpsKnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OpsKnowledgeServiceImpl implements OpsKnowledgeService {

    private final OpsKnowledgeMapper knowledgeMapper;

    @Override
    public PageResult<OpsKnowledge> list(String keyword, String category, Long hostId, Long serviceId, int page, int size) {
        LambdaQueryWrapper<OpsKnowledge> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(OpsKnowledge::getTitle, keyword)
                    .or().like(OpsKnowledge::getContent, keyword)
                    .or().like(OpsKnowledge::getTags, keyword));
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(OpsKnowledge::getCategory, category);
        }
        if (hostId != null) {
            wrapper.eq(OpsKnowledge::getHostId, hostId);
        }
        if (serviceId != null) {
            wrapper.eq(OpsKnowledge::getServiceId, serviceId);
        }
        wrapper.orderByDesc(OpsKnowledge::getCreatedAt);

        Page<OpsKnowledge> p = knowledgeMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    @Override
    public OpsKnowledge getById(Long id) {
        OpsKnowledge k = knowledgeMapper.selectById(id);
        if (k == null) {
            throw new NotFoundException("运维知识", id);
        }
        // 阅读量 +1
        OpsKnowledge update = new OpsKnowledge();
        update.setId(id);
        update.setViewCount((k.getViewCount() == null ? 0 : k.getViewCount()) + 1);
        knowledgeMapper.updateById(update);
        k.setViewCount(update.getViewCount());
        return k;
    }

    @Override
    public OpsKnowledge create(OpsKnowledgeRequest request) {
        OpsKnowledge k = new OpsKnowledge();
        copyFromRequest(k, request);
        k.setViewCount(0);
        knowledgeMapper.insert(k);
        return k;
    }

    @Override
    public OpsKnowledge update(Long id, OpsKnowledgeRequest request) {
        OpsKnowledge k = knowledgeMapper.selectById(id);
        if (k == null) {
            throw new NotFoundException("运维知识", id);
        }
        copyFromRequest(k, request);
        knowledgeMapper.updateById(k);
        return k;
    }

    @Override
    public void delete(Long id) {
        OpsKnowledge k = knowledgeMapper.selectById(id);
        if (k == null) {
            throw new NotFoundException("运维知识", id);
        }
        knowledgeMapper.deleteById(id);
    }

    private void copyFromRequest(OpsKnowledge k, OpsKnowledgeRequest r) {
        k.setTitle(r.getTitle());
        k.setCategory(r.getCategory());
        k.setContent(r.getContent());
        k.setTags(r.getTags());
        k.setHostId(r.getHostId());
        k.setServiceId(r.getServiceId());
        k.setAuthor(r.getAuthor());
    }
}
