package com.kb.knowledge.dto.folder;

import lombok.Data;

import java.util.List;

/**
 * 资源树节点（文件夹 + 资源统一树）
 */
@Data
public class ResourceTreeNode {

    private Long id;

    private String name;

    /** folder / doc / file / web */
    private String type;

    /** doc 才有: html / markdown */
    private String format;

    /** web 才有 */
    private String url;

    /** folder 才有 */
    private List<ResourceTreeNode> children;
}
