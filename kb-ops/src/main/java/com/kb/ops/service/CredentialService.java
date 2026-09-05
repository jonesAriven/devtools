package com.kb.ops.service;

import com.marschat.common.page.PageResult;
import com.kb.ops.dto.CredentialRequest;
import com.kb.ops.entity.Credential;

public interface CredentialService {

    PageResult<Credential> list(String type, String keyword, int page, int size);

    Credential getById(Long id, boolean revealPassword);

    Credential create(CredentialRequest request);

    Credential update(Long id, CredentialRequest request);

    void delete(Long id);
}
