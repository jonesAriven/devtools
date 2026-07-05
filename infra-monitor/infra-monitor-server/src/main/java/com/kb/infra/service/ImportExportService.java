package com.kb.infra.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.kb.common.exception.BusinessException;
import com.kb.infra.entity.InfraItem;
import com.kb.infra.repository.InfraItemRepository;
import com.kb.infra.util.CryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportExportService {

    private final InfraItemRepository repository;
    private final CryptoUtil cryptoUtil;

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    public Map<String, Object> importData(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new BusinessException("文件名不能为空");
        }

        try {
            byte[] bytes = file.getBytes();
            String content = new String(bytes);

            Map<String, Object> data;
            if (filename.endsWith(".yaml") || filename.endsWith(".yml")) {
                data = YAML_MAPPER.readValue(content, Map.class);
            } else if (filename.endsWith(".json")) {
                data = JSON_MAPPER.readValue(content, Map.class);
            } else {
                throw new BusinessException("不支持的文件格式，仅支持 .json / .yaml / .yml");
            }

            Map<String, Object> counts = new HashMap<>();
            counts.put("hosts", importType("host", (List<Map<String, Object>>) data.get("hosts")));
            counts.put("credentials", importCredentials((List<Map<String, Object>>) data.get("credentials")));
            counts.put("configs", importType("config", (List<Map<String, Object>>) data.get("configs")));
            counts.put("services", importType("service", (List<Map<String, Object>>) data.get("services")));

            log.info("导入完成: {}", counts);
            return counts;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("导入失败", e);
            throw new BusinessException("导入失败: " + e.getMessage());
        }
    }

    private int importType(String type, List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) return 0;
        int count = 0;
        int sort = getMaxSortOrder(type) + 1;
        for (Map<String, Object> item : items) {
            InfraItem infra = new InfraItem();
            infra.setType(type);
            infra.setName((String) item.get("name"));
            infra.setCategory(item.get("category") != null ? (String) item.get("category") : "其它");
            infra.setDescription(item.get("description") != null ? (String) item.get("description") : "");
            Map<String, Object> extra = item.get("data") != null ?
                    new HashMap<>((Map<String, Object>) item.get("data")) : new HashMap<>();
            for (String key : item.keySet()) {
                if (!List.of("name", "category", "description", "data").contains(key)) {
                    extra.put(key, item.get(key));
                }
            }
            infra.setExtra(extra);
            infra.setSortOrder(sort++);
            infra.setDeleted(0);
            infra.setCreatedAt(LocalDateTime.now());
            infra.setUpdatedAt(LocalDateTime.now());
            repository.save(infra);
            count++;
        }
        return count;
    }

    private int importCredentials(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) return 0;
        int count = 0;
        int sort = getMaxSortOrder("credential") + 1;
        for (Map<String, Object> item : items) {
            InfraItem infra = new InfraItem();
            infra.setType("credential");
            infra.setName((String) item.get("name"));
            infra.setCategory(item.get("type") != null ? (String) item.get("type") :
                    (item.get("category") != null ? (String) item.get("category") : "OTHER"));
            infra.setDescription(item.get("description") != null ? (String) item.get("description") : "");
            Map<String, Object> extra = item.get("data") != null ?
                    new HashMap<>((Map<String, Object>) item.get("data")) : new HashMap<>();
            for (String key : item.keySet()) {
                if (!List.of("name", "category", "description", "data", "password", "secretKey").contains(key)) {
                    extra.put(key, item.get(key));
                }
            }
            if (item.containsKey("password")) {
                extra.put("passwordEncrypted", cryptoUtil.encrypt(String.valueOf(item.get("password"))));
            }
            if (item.containsKey("secretKey")) {
                extra.put("secretKeyEncrypted", cryptoUtil.encrypt(String.valueOf(item.get("secretKey"))));
            }
            infra.setExtra(extra);
            infra.setSortOrder(sort++);
            infra.setDeleted(0);
            infra.setCreatedAt(LocalDateTime.now());
            infra.setUpdatedAt(LocalDateTime.now());
            repository.save(infra);
            count++;
        }
        return count;
    }

    private int getMaxSortOrder(String type) {
        List<InfraItem> list = repository.findByTypeAndDeletedOrderBySortOrderAscCreatedAtDesc(type, 0);
        return list.isEmpty() ? 0 : list.get(0).getSortOrder();
    }

    public Map<String, Object> exportData() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", "1.0");
        result.put("exportedAt", LocalDateTime.now().toString());
        result.put("hosts", exportType("host"));
        result.put("credentials", exportCredentials());
        result.put("configs", exportType("config"));
        result.put("services", exportType("service"));
        return result;
    }

    private List<Map<String, Object>> exportType(String type) {
        List<InfraItem> items = repository.findByTypeAndDeletedOrderBySortOrderAscCreatedAtDesc(type, 0);
        List<Map<String, Object>> list = new ArrayList<>();
        for (InfraItem item : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", item.getName());
            m.put("category", item.getCategory());
            m.put("description", item.getDescription());
            if (item.getExtra() != null && !item.getExtra().isEmpty()) {
                Map<String, Object> filtered = new LinkedHashMap<>();
                for (Map.Entry<String, Object> e : item.getExtra().entrySet()) {
                    if (!e.getKey().endsWith("Encrypted")) {
                        filtered.put(e.getKey(), e.getValue());
                    }
                }
                if (!filtered.isEmpty()) {
                    m.put("data", filtered);
                }
            }
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> exportCredentials() {
        List<InfraItem> items = repository.findByTypeAndDeletedOrderBySortOrderAscCreatedAtDesc("credential", 0);
        List<Map<String, Object>> list = new ArrayList<>();
        for (InfraItem item : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", item.getName());
            m.put("type", item.getCategory());
            m.put("description", item.getDescription());
            if (item.getExtra() != null && !item.getExtra().isEmpty()) {
                Map<String, Object> filtered = new LinkedHashMap<>();
                for (Map.Entry<String, Object> e : item.getExtra().entrySet()) {
                    if (!e.getKey().endsWith("Encrypted")) {
                        filtered.put(e.getKey(), e.getValue());
                    }
                }
                if (!filtered.isEmpty()) {
                    m.put("data", filtered);
                }
            }
            list.add(m);
        }
        return list;
    }
}
