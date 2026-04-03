package com.ktguru.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResolveUnansweredDto {

    @NotBlank
    private String resolutionAnswer;

    private String resolvedBy;
}
