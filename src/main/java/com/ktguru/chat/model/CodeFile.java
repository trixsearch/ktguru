package com.ktguru.chat.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "code_files")
public class CodeFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String language; // e.g., JAVA, XML, YAML

    @Column(updatable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();
}