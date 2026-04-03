package com.ktguru.chat.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "code_chunks")
public class CodeChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private CodeFile codeFile;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false)
    private Integer chunkIndex;
}