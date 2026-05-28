package com.flowbridge.controller;

import com.flowbridge.dto.AccountOpeningRequest;
import com.flowbridge.dto.AuditLogResponse;
import com.flowbridge.dto.WorkflowDetailResponse;
import com.flowbridge.dto.WorkflowResponse;
import com.flowbridge.service.RetryService;
import com.flowbridge.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final RetryService retryService;

    public WorkflowController(WorkflowService workflowService, RetryService retryService) {
        this.workflowService = workflowService;
        this.retryService = retryService;
    }

    @PostMapping("/account-opening")
    public ResponseEntity<WorkflowResponse> createAccountOpeningWorkflow(
            @Valid @RequestBody AccountOpeningRequest request
    ) {
        WorkflowResponse response = workflowService.createAccountOpeningWorkflow(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{workflowId}")
    public ResponseEntity<WorkflowDetailResponse> getWorkflow(@PathVariable Long workflowId) {
        WorkflowDetailResponse response = workflowService.getWorkflow(workflowId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{workflowId}/audit-logs")
    public ResponseEntity<List<AuditLogResponse>> getAuditLogs(@PathVariable Long workflowId) {
        List<AuditLogResponse> response = workflowService.getAuditLogs(workflowId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{workflowId}/retry")
    public ResponseEntity<WorkflowResponse> retryWorkflow(@PathVariable Long workflowId) {
        WorkflowResponse response = retryService.retryWorkflow(workflowId);
        return ResponseEntity.accepted().body(response);
    }
}
