package com.ktguru.chat.controller;

import com.ktguru.chat.dto.ResolveUnansweredDto;
import com.ktguru.chat.model.UnansweredQuestion;
import com.ktguru.chat.repository.UnansweredQuestionRepository;
import com.ktguru.chat.service.VectorIndexService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/unanswered")
@RequiredArgsConstructor
public class UnansweredController {

    private final UnansweredQuestionRepository unansweredQuestionRepository;
    private final VectorIndexService vectorIndexService;

    @GetMapping
    public List<UnansweredQuestion> listUnresolved() {
        return unansweredQuestionRepository.findByStatusOrderByCreatedAtDesc(UnansweredQuestion.QuestionStatus.UNRESOLVED);
    }

    @PostMapping("/{id}/resolve")
    public UnansweredQuestion resolve(@PathVariable Long id, @Valid @RequestBody ResolveUnansweredDto body) {
        UnansweredQuestion q = unansweredQuestionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown id: " + id));
        q.setStatus(UnansweredQuestion.QuestionStatus.RESOLVED);
        q.setResolutionAnswer(body.getResolutionAnswer());
        q.setResolvedBy(body.getResolvedBy());
        q.setResolvedAt(LocalDateTime.now());
        UnansweredQuestion saved = unansweredQuestionRepository.save(q);
        vectorIndexService.indexSmeQa(saved.getQuestion(), saved.getResolutionAnswer());
        return saved;
    }
}
