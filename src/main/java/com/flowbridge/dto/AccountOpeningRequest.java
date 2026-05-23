package com.flowbridge.dto;

import com.flowbridge.enums.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
public class AccountOpeningRequest {

    @NotBlank
    private String clientId;

    @NotBlank
    private String fullName;

    @NotNull
    private LocalDate dateOfBirth;

    @NotNull
    private AccountType accountType;

    private String advisorCode;

}
