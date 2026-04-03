package com.ktguru.chat.repository;

import com.ktguru.chat.model.UnansweredQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UnansweredQuestionRepository extends JpaRepository<UnansweredQuestion, Long> {

    List<UnansweredQuestion> findByStatusOrderByCreatedAtDesc(UnansweredQuestion.QuestionStatus status);
}
