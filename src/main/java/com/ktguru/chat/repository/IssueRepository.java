package com.ktguru.chat.repository;

import com.ktguru.chat.model.Issue;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IssueRepository extends JpaRepository<Issue, Long> {

    @Query("""
            SELECT i FROM Issue i
            WHERE LOWER(i.subject) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(i.resolution) LIKE LOWER(CONCAT('%', :q, '%'))
            ORDER BY i.resolvedAt DESC
            """)
    List<Issue> searchByText(@Param("q") String q, Pageable pageable);
}
