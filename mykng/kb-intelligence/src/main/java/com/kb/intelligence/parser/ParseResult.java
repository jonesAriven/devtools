package com.kb.intelligence.parser;

import com.kb.intelligence.entity.*;
import com.kb.intelligence.mongo.doc.KnContent;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ParseResult {
    private KnDoc docMeta;
    private List<KnHost> hosts = new ArrayList<>();
    private List<KnService> services = new ArrayList<>();
    private List<KnPort> ports = new ArrayList<>();
    private List<KnCredential> credentials = new ArrayList<>();
    private List<KnDomain> domains = new ArrayList<>();
    private List<KnDependency> dependencies = new ArrayList<>();
    private List<KnCommand> commands = new ArrayList<>();
    private List<KnTimeline> timelines = new ArrayList<>();
    private KnContent content;
}
