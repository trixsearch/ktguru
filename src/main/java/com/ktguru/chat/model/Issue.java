package com.ktguru.chat.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "issues")
public class Issue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500, nullable = false)
    private String subject;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String resolution;

    private String raisedBy;
    private String resolvedBy;

    @Column(nullable = false)
    private LocalDateTime raisedAt;

    @Column(nullable = false)
    private LocalDateTime resolvedAt;

    private Integer resolutionTimeMinutes;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}