package com.am.sbextracts.service.integration;

import com.am.sbextracts.client.BambooHrApiClient;
import com.am.sbextracts.model.Employee;
import com.am.sbextracts.model.Report;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    @Value("${bamboo.reportId}")
    private final String reportId;

    private final BambooHrApiClient bambooHrApiClient;
    private final HeaderService headerService;

    public Map<String, String> getEmployees() {
        Report report = bambooHrApiClient.getEmployees(headerService.getHeaderForBchApi(), reportId);
        if (CollectionUtils.isEmpty(report.getEmployees())) {
            throw new IllegalArgumentException("employee list is empty");
        }
        return report.getEmployees().stream().collect(Collectors.toMap(Employee::getInn, Employee::getId));
    }
}
