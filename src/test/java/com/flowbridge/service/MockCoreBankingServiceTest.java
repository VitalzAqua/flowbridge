package com.flowbridge.service;

import com.flowbridge.dto.CoreBankingPayload;
import com.flowbridge.dto.CoreBankingResponse;
import com.flowbridge.enums.ExternalSystemStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockCoreBankingServiceTest {

    private final MockCoreBankingService mockCoreBankingService = new MockCoreBankingService();

    @Test
    void returnsSuccessForValidCoreBankingPayload() {
        CoreBankingPayload payload = new CoreBankingPayload(
                "C123",
                "Alice Chen",
                "SAV001",
                "ADV001"
        );

        CoreBankingResponse response = mockCoreBankingService.openAccount(payload);

        assertThat(response.getStatus()).isEqualTo(ExternalSystemStatus.SUCCESS);
        assertThat(response.getExternalReferenceId()).startsWith("CORE-");
        assertThat(response.getErrorCode()).isNull();
        assertThat(response.getMessage()).isEqualTo("Account created successfully");
    }

    @Test
    void returnsFailureWhenProductCodeIsMissing() {
        CoreBankingPayload payload = new CoreBankingPayload(
                "C123",
                "Alice Chen",
                null,
                "ADV001"
        );

        CoreBankingResponse response = mockCoreBankingService.openAccount(payload);

        assertThat(response.getStatus()).isEqualTo(ExternalSystemStatus.FAILED);
        assertThat(response.getErrorCode()).isEqualTo("MISSING_PRODUCT_CODE");
        assertThat(response.getMessage()).isEqualTo("Product code is required by core banking");
    }

    @Test
    void returnsFailureWhenCustomerIdStartsWithFail() {
        CoreBankingPayload payload = new CoreBankingPayload(
                "FAIL-123",
                "Alice Chen",
                "SAV001",
                "ADV001"
        );

        CoreBankingResponse response = mockCoreBankingService.openAccount(payload);

        assertThat(response.getStatus()).isEqualTo(ExternalSystemStatus.FAILED);
        assertThat(response.getErrorCode()).isEqualTo("CORE_BANKING_REJECTED");
    }

    @Test
    void returnsFailureWhenCustomerIdStartsWithDup() {
        CoreBankingPayload payload = new CoreBankingPayload(
                "DUP-123",
                "Alice Chen",
                "SAV001",
                "ADV001"
        );

        CoreBankingResponse response = mockCoreBankingService.openAccount(payload);

        assertThat(response.getStatus()).isEqualTo(ExternalSystemStatus.FAILED);
        assertThat(response.getErrorCode()).isEqualTo("DUPLICATE_CUSTOMER");
        assertThat(response.getMessage()).isEqualTo("Customer already has this account type");
    }
}
