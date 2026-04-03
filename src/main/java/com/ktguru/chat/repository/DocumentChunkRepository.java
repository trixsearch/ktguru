package com.ktguru.chat.repository;
import com.ktguru.chat.model.DocumentChunk;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long>{
	
	// humari personal method(custom method) to fetch all the chunks of a specific document
	List<DocumentChunk> findByDocument_IdOrderByChunkIndexAsc(Long documentId);

}
