package com.flowbridge.controller;

import com.flowbridge.dto.AccountOpeningRequest;
import com.flowbridge.dto.WorkflowResponse;
import com.flowbridge.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping("/account-opening")
    public ResponseEntity<WorkflowResponse> createAccountOpeningWorkflow(
            @Valid @RequestBody AccountOpeningRequest request
    ) {
        WorkflowResponse response = workflowService.createAccountOpeningWorkflow(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
