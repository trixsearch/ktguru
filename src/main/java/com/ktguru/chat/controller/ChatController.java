package com.ktguru.chat.controller;

import com.ktguru.chat.dto.ChatRequestDto;
import com.ktguru.chat.dto.ChatResponseDto;
import com.ktguru.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ChatResponseDto chat(@Valid @RequestBody ChatRequestDto body) {
        ChatService.ChatResult r = chatService.chat(body.getSessionId(), body.getMessage());
        return new ChatResponseDto(r.sessionId(), r.type().name(), r.content());
    }
}
