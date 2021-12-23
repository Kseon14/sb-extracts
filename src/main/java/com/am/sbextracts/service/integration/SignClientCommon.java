package com.am.sbextracts.service.integration;

import com.am.sbextracts.client.BambooHrSignClient;
import com.am.sbextracts.model.Folder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class SignClientCommon {

    private final BambooHrSignClient bambooHrSignClient;

    public Folder getFolderContent(int offset, int sectionId, Map<String, String> bchHeaders) {
        return bambooHrSignClient.getFolderContent(bchHeaders, BambooHrSignClient.FolderParams.of(sectionId, offset));
    }
}
