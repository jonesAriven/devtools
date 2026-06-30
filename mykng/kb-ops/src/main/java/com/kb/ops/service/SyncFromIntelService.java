package com.kb.ops.service;

import com.kb.ops.dto.SyncFromIntelRequest;
import com.kb.ops.dto.SyncFromIntelResult;

public interface SyncFromIntelService {
    SyncFromIntelResult syncFromIntelligence(SyncFromIntelRequest request);
}
