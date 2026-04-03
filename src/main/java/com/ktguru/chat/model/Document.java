package com.ktguru.chat.model;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "documents")
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceType sourceType;

    private String uploadedBy;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum SourceType {
        DOCX, TXT, ODT
    }
}