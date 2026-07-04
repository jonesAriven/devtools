package com.kb.common.exception;

public class NotFoundException extends BusinessException {
    public NotFoundException(String resource, Long id) {
        super(404, resource + " 不存在: " + id);
    }

    public NotFoundException(String message) {
        super(404, message);
    }
}
