package com.flowbridge.dto;

import com.flowbridge.enums.ExternalSystemStatus;
import lombok.Getter;

@Getter
public class CoreBankingResponse {

    private final String externalReferenceId;
    private final ExternalSystemStatus status;
    private final String errorCode;
    private final String message;

    public CoreBankingResponse(
            String externalReferenceId,
            ExternalSystemStatus status,
            String errorCode,
            String message
    ) {
        this.externalReferenceId = externalReferenceId;
        this.status = status;
        this.errorCode = errorCode;
        this.message = message;
    }

    public boolean isSuccessful() {
        return status == ExternalSystemStatus.SUCCESS;
    }
}
