package com.kb.intelligence.service;

import com.kb.intelligence.parser.ParseResult;

public interface EntityPersister {

    Long persist(ParseResult result);
}
