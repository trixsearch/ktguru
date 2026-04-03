package com.ktguru.chat.repository;

import com.ktguru.chat.model.CodeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CodeChunkRepository extends JpaRepository<CodeChunk, Long> {

    List<CodeChunk> findByCodeFile_IdOrderByChunkIndexAsc(Long codeFileId);
}
