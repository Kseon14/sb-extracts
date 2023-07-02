package com.am.sbextracts.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Value;

@Value
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReportData {
  Map<String, TemplateData> templateSignatureReport;
  int[] reportByIds;

  @Value
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class TemplateData {
    @JsonProperty("template_id")
    String templateId;
    @JsonProperty("workflow_instance_id")
    String workflowInstanceId;
    String status;
    @JsonProperty("requested_date")
    String requestedDate;
    @JsonProperty("signed_date")
    String signedDate;
    @JsonProperty("requester_name")
    String requesterName;
    @JsonProperty("requester_user_id")
    String requesterUserId;
    @JsonProperty("employee_file_id")
    String employeeFileId;
    @JsonProperty("employee_id")
    String employeeId;
    @JsonProperty("employee_name")
    String employeeName;
    Map<String, SignerData> signers;
    @JsonProperty("employee_file")
    EmployeeFile employeeFile;
  }

  @Value
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SignerData {
    @JsonProperty("employee_file_id")
    String employeeFileId;
    String position;
    @JsonProperty("employee_name")
    String employeeName;
    @JsonProperty("completed_signer_name")
    String completedSignerName;
    @JsonProperty("signer_status")
    String signerStatus;
    @JsonProperty("signer_sent")
    String signerSent;
    @JsonProperty("signer_signed")
    String signerSigned;
  }

  @Value
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class EmployeeFile {
    String id;
    String fid;
    String name;
    @JsonProperty("original_file_name")
    String originalFileName;
    @JsonProperty("created_user")
    String createdUser;
    @JsonProperty("most_recent_employee_file_data_id")
    String mostRecentEmployeeFileDataId;
    @JsonProperty("fd_id")
    String fdId;
  }
}

