package com.flowbridge.service;

import com.flowbridge.dto.AccountOpeningRequest;
import com.flowbridge.dto.CoreBankingPayload;
import com.flowbridge.enums.AccountType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MappingServiceTest {

    private final MappingService mappingService = new MappingService();

    @Test
    void mapsSavingsAccountOpeningRequestToCoreBankingPayload() {
        AccountOpeningRequest request = accountOpeningRequest(AccountType.SAVINGS, "ADV001");

        CoreBankingPayload payload = mappingService.mapAccountOpeningRequest(request);

        assertThat(payload.getCustomerId()).isEqualTo("C123");
        assertThat(payload.getCustomerName()).isEqualTo("Alice Chen");
        assertThat(payload.getProductCode()).isEqualTo("SAV001");
        assertThat(payload.getAdvisorId()).isEqualTo("ADV001");
    }

    @Test
    void mapsChequingAccountTypeToProductCode() {
        AccountOpeningRequest request = accountOpeningRequest(AccountType.CHEQUING, null);

        CoreBankingPayload payload = mappingService.mapAccountOpeningRequest(request);

        assertThat(payload.getProductCode()).isEqualTo("CHQ001");
        assertThat(payload.getAdvisorId()).isNull();
    }

    @Test
    void mapsTfsaAccountTypeToProductCode() {
        AccountOpeningRequest request = accountOpeningRequest(AccountType.TFSA, "ADV002");

        CoreBankingPayload payload = mappingService.mapAccountOpeningRequest(request);

        assertThat(payload.getProductCode()).isEqualTo("TFSA001");
    }

    private AccountOpeningRequest accountOpeningRequest(AccountType accountType, String advisorCode) {
        AccountOpeningRequest request = new AccountOpeningRequest();
        request.setClientId("C123");
        request.setFullName("Alice Chen");
        request.setAccountType(accountType);
        request.setAdvisorCode(advisorCode);
        return request;
    }
}
