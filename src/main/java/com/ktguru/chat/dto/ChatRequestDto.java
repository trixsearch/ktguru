package com.ktguru.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequestDto {

    private Long sessionId;

    @NotBlank
    private String message;
}
