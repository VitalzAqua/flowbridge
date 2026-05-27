package com.flowbridge.service;

import com.flowbridge.dto.CoreBankingPayload;
import com.flowbridge.dto.CoreBankingResponse;
import com.flowbridge.enums.ExternalSystemStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MockCoreBankingService {

    public CoreBankingResponse openAccount(CoreBankingPayload payload) {
        if (payload.getProductCode() == null || payload.getProductCode().isBlank()) {
            return failed("MISSING_PRODUCT_CODE", "Product code is required by core banking");
        }

        if (payload.getCustomerId() != null && payload.getCustomerId().startsWith("FAIL")) {
            return failed("CORE_BANKING_REJECTED", "Core banking rejected the account-opening request");
        }

        if (payload.getCustomerId() != null && payload.getCustomerId().startsWith("DUP")) {
            return failed("DUPLICATE_CUSTOMER", "Customer already has this account type");
        }

        return new CoreBankingResponse(
                "CORE-" + UUID.randomUUID(),
                ExternalSystemStatus.SUCCESS,
                null,
                "Account created successfully"
        );
    }

    private CoreBankingResponse failed(String errorCode, String message) {
        return new CoreBankingResponse(
                null,
                ExternalSystemStatus.FAILED,
                errorCode,
                message
        );
    }
}
