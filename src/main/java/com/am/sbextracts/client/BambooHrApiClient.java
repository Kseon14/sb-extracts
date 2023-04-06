package com.am.sbextracts.client;

import com.am.sbextracts.FeignClientFormPostConfig;
import com.am.sbextracts.model.Report;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@FeignClient(value = "bambooApiHr", configuration = FeignClientFormPostConfig.class, url = "https://api.bamboohr.com/api/gateway.php/${COMPANY_NAME}/v1")
public interface BambooHrApiClient {

    @GetMapping(value = "reports/{reportId}?format=JSON",
            produces = "application/json")
    Report getEmployees(@RequestHeader Map<String, String> headerMap, @PathVariable String reportId);

    @PostMapping(value = "/employees/{employeeId}/files",
            consumes = MULTIPART_FORM_DATA_VALUE, produces = APPLICATION_JSON_VALUE)
    void uploadFile(@RequestHeader Map<String, String> headerMap, @PathVariable String employeeId,
                    @RequestBody Map<String, ?> request);

}