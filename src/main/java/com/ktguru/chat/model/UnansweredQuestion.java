package com.ktguru.chat.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "unanswered_questions")
public class UnansweredQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String question;

    @Enumerated(EnumType.STRING)
    private QuestionStatus status = QuestionStatus.UNRESOLVED;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime resolvedAt;
    private String resolvedBy;
    
    @Column(columnDefinition = "TEXT")
    private String resolutionAnswer;

    public enum QuestionStatus {
        UNRESOLVED, RESOLVED
    }
}