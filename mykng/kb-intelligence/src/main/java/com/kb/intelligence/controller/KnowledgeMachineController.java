package com.kb.intelligence.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marschat.common.result.Result;
import com.kb.intelligence.dto.request.KnowledgeSearchRequest;
import com.kb.intelligence.dto.response.*;
import com.kb.intelligence.service.KnowledgeQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import com.kb.intelligence.entity.KnCredential;
import com.kb.intelligence.entity.KnDependency;
import com.kb.intelligence.entity.KnDomain;
import com.kb.intelligence.entity.KnHost;
import com.kb.intelligence.entity.KnPort;
import com.kb.intelligence.entity.KnService;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/intelligence/machine")
@RequiredArgsConstructor
public class KnowledgeMachineController {

    private final KnowledgeQueryService queryService;

    @GetMapping("/docs")
    public Result<Page<DocIndexVO>> listDocs(
            @RequestParam(required = false) String docType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(queryService.listDocs(docType, category, tag, page, size));
    }

    @GetMapping("/docs/{docId}/meta")
    public Result<DocIndexVO> getDocMeta(@PathVariable Long docId) {
        DocIndexVO meta = queryService.getDocMeta(docId);
        return meta != null ? Result.ok(meta) : Result.fail(404, "文档不存在");
    }

    @GetMapping("/docs/{docId}/entities")
    public Result<DocEntitiesVO> getEntities(@PathVariable Long docId) {
        return Result.ok(queryService.getDocEntities(docId));
    }

    @GetMapping("/docs/{docId}/content")
    public Result<DocContentVO> getContent(@PathVariable Long docId) {
        DocContentVO content = queryService.getDocContent(docId);
        return content != null ? Result.ok(content) : Result.fail(404, "文档内容不存在");
    }

    @GetMapping("/entities/hosts")
    public Result<List<DocEntitiesVO.HostVO>> listHosts(
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String role) {
        return Result.ok(queryService.listHosts(ip, name, role));
    }

    @GetMapping("/entities/services")
    public Result<List<DocEntitiesVO.ServiceVO>> listServices(
            @RequestParam(required = false) Long hostId,
            @RequestParam(required = false) String name) {
        return Result.ok(queryService.listServices(hostId, name));
    }

    @GetMapping("/entities/commands")
    public Result<List<DocEntitiesVO.CommandVO>> listCommands(
            @RequestParam(required = false) Long docId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String riskLevel) {
        return Result.ok(queryService.listCommands(docId, category, riskLevel));
    }

    @GetMapping("/entities/timelines")
    public Result<List<DocEntitiesVO.TimelineVO>> listTimelines(
            @RequestParam(required = false) Long docId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String eventType) {
        return Result.ok(queryService.listTimelines(docId, severity, eventType));
    }

    @GetMapping("/entities/ports")
    public Result<List<DocEntitiesVO.PortVO>> listPorts(
            @RequestParam(required = false) Long hostId,
            @RequestParam(required = false) Integer exposed) {
        return Result.ok(queryService.listPorts(hostId, exposed));
    }

    @GetMapping("/entities/credentials")
    public Result<List<DocEntitiesVO.CredentialVO>> listCredentials(
            @RequestParam(required = false) Long hostId,
            @RequestParam(required = false) String credType) {
        return Result.ok(queryService.listCredentials(hostId, credType));
    }

    @GetMapping("/entities/domains")
    public Result<List<DocEntitiesVO.DomainVO>> listDomains(
            @RequestParam(required = false) String status) {
        return Result.ok(queryService.listDomains(status));
    }

    @PostMapping("/search")
    public Result<List<SearchResultVO>> search(@RequestBody KnowledgeSearchRequest request) {
        return Result.ok(queryService.search(
                request.getQuery(),
                request.getDocTypes(),
                request.getTags(),
                request.getPage() != null ? request.getPage() : 1,
                request.getSize() != null ? request.getSize() : 20
        ));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        return Result.ok(queryService.getStats());
    }

    // ============ 内部同步接口（供内部同步用（原 kb-ops 已剥离），返回完整实体含敏感字段） ============

    @GetMapping("/internal/hosts")
    public Result<List<KnHost>> listAllHostsInternal() {
        return Result.ok(queryService.listAllHosts());
    }

    @GetMapping("/internal/services")
    public Result<List<KnService>> listAllServicesInternal() {
        return Result.ok(queryService.listAllServices());
    }

    @GetMapping("/internal/ports")
    public Result<List<KnPort>> listAllPortsInternal() {
        return Result.ok(queryService.listAllPorts());
    }

    @GetMapping("/internal/credentials")
    public Result<List<KnCredential>> listAllCredentialsInternal() {
        return Result.ok(queryService.listAllCredentials());
    }

    @GetMapping("/internal/domains")
    public Result<List<KnDomain>> listAllDomainsInternal() {
        return Result.ok(queryService.listAllDomains());
    }

    @GetMapping("/internal/dependencies")
    public Result<List<KnDependency>> listAllDependenciesInternal() {
        return Result.ok(queryService.listAllDependencies());
    }
}
