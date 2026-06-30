package com.kb.ops.dto;

import lombok.Data;
import java.util.List;

@Data
public class SyncFromIntelRequest {
    private boolean override = false;
    private List<String> entityTypes;
}
