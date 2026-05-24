package com.flowbridge.service;

import com.flowbridge.dto.AccountOpeningRequest;
import com.flowbridge.dto.CoreBankingPayload;
import com.flowbridge.enums.AccountType;
import org.springframework.stereotype.Service;

@Service
public class MappingService {

    public CoreBankingPayload mapAccountOpeningRequest(AccountOpeningRequest request) {
        return new CoreBankingPayload(
                request.getClientId(),
                request.getFullName(),
                toProductCode(request.getAccountType()),
                request.getAdvisorCode()
        );
    }

    private String toProductCode(AccountType accountType) {
        return switch (accountType) {
            case SAVINGS -> "SAV001";
            case CHEQUING -> "CHQ001";
            case TFSA -> "TFSA001";
        };
    }
}
