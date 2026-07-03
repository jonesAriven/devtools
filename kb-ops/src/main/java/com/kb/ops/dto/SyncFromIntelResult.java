package com.kb.ops.dto;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

@Data
public class SyncFromIntelResult {
    private SyncStats host = new SyncStats();
    private SyncStats service = new SyncStats();
    private SyncStats port = new SyncStats();
    private SyncStats credential = new SyncStats();
    private SyncStats domain = new SyncStats();
    private SyncStats dependency = new SyncStats();
    private long durationMs;
    private String error;

    @Data
    public static class SyncStats {
        private int total;
        private int created;
        private int updated;
        private int skipped;
        private int failed;

        public void incrementCreated() { created++; }
        public void incrementUpdated() { updated++; }
        public void incrementSkipped() { skipped++; }
        public void incrementFailed() { failed++; }
    }
}
