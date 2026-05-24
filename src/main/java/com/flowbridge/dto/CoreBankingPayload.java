package com.flowbridge.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class CoreBankingPayload {

    @JsonProperty("customer_id")
    private final String customerId;

    @JsonProperty("customer_name")
    private final String customerName;

    @JsonProperty("product_code")
    private final String productCode;

    @JsonProperty("advisor_id")
    private final String advisorId;

    public CoreBankingPayload(
            String customerId,
            String customerName,
            String productCode,
            String advisorId
    ) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.productCode = productCode;
        this.advisorId = advisorId;
    }
}
