package com.am.sbextracts.client;

import com.am.sbextracts.FeignClientFormPostConfig;
import com.am.sbextracts.model.Folder;
import com.am.sbextracts.model.Response;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_PDF_VALUE;

@FeignClient(value = "bambooSignHr", configuration = FeignClientFormPostConfig.class, url = "https://${app.company.name}.bamboohr.com/ajax")
public interface BambooHrSignClient {

    @RequestMapping(method = RequestMethod.POST, value = "files/send_signature_request.php?route=sendSignatureRequest&workflowId={workflowId}", consumes = APPLICATION_FORM_URLENCODED_VALUE)
    feign.Response signatureRequest(@RequestHeader Map<String, String> headerMap, Map<String, ?> request,
            @PathVariable String workflowId);

    @RequestMapping(method = RequestMethod.POST, value = "esignature/create_template.php", consumes = APPLICATION_FORM_URLENCODED_VALUE)
    feign.Response createTemplate(@RequestHeader Map<String, String> headerMap, CrtRequest request);

    @RequestMapping(method = RequestMethod.POST, value = "esignature/update_template.php", consumes = APPLICATION_FORM_URLENCODED_VALUE, produces = APPLICATION_JSON_VALUE)
    feign.Response updateTemplate(@RequestHeader Map<String, String> headerMap, UpdateRequest request);

    // sort=nameD
    // sort=dateA
    @RequestMapping(method = RequestMethod.GET, value = "dialog/files/load_section_files.php?sort=nameD")
    Folder getFolderContent(@RequestHeader Map<String, String> headerMap, @SpringQueryMap FolderParams params);

    @RequestMapping(method = RequestMethod.GET, value = "esignature/get_completed_svgs.php", consumes = APPLICATION_JSON_VALUE)
    Response getCompleted(@RequestHeader Map<String, String> headerMap, @SpringQueryMap CompleteParams params);

    @RequestMapping(method = RequestMethod.GET, value = "esignature/check_for_completed_svgs.php", consumes = APPLICATION_JSON_VALUE)
    Response isReady(@RequestHeader Map<String, String> headerMap, @SpringQueryMap CompleteParams params);

    @RequestMapping(method = RequestMethod.GET, value = "fetcher.php?doc={templateFileId}&format=pdf", consumes = APPLICATION_PDF_VALUE)
    byte[] getPdf(@RequestHeader Map<String, String> headerMap, @PathVariable int templateFileId);

    @Value(staticConstructor = "of")
    class CompleteParams {
        int file_id;
    }

    @Getter
    @AllArgsConstructor
    class CrtRequest {
        boolean ajax;
        int file_data_id;
    }

    @Getter
    @AllArgsConstructor
    class UpdateRequest {
        boolean ajax;
        int esignatureTemplateId;
        String fields;
    }

    @Value(staticConstructor = "of")
    class FolderParams {
        int sectionId;
        int offset;
    }

}