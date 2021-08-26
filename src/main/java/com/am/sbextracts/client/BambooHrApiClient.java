package com.am.sbextracts.client;

import com.am.sbextracts.FeignClientFormPostConfig;
import com.am.sbextracts.model.Report;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@FeignClient(value = "bambooApiHr", configuration = FeignClientFormPostConfig.class, url = "https://api.bamboohr.com/api/gateway.php/squadukraine/v1")
public interface BambooHrApiClient {

    @RequestMapping(method = RequestMethod.GET, value = "reports/{reportId}?format=JSON", produces = "application/json")
    Report getEmployees(@RequestHeader Map<String, String> headerMap, @PathVariable String reportId);

    @RequestMapping(method = RequestMethod.POST, value = "/employees/{employeeId}/files", consumes = MULTIPART_FORM_DATA_VALUE, produces = APPLICATION_JSON_VALUE)
    void uploadFile(@RequestHeader Map<String, String> headerMap, @PathVariable int employeeId,
            @RequestBody Map<String, ?> request);

}