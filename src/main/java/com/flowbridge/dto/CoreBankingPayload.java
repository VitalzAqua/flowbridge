package com.flowbridge.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
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

    @JsonCreator
    public CoreBankingPayload(
            @JsonProperty("customer_id") String customerId,
            @JsonProperty("customer_name") String customerName,
            @JsonProperty("product_code") String productCode,
            @JsonProperty("advisor_id") String advisorId
    ) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.productCode = productCode;
        this.advisorId = advisorId;
    }
}
