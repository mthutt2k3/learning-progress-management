package com.learning.progress.repository;

import com.learning.progress.entity.GradingQuestion;
import com.learning.progress.entity.SubmissionQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GradingQuestionRepository  extends JpaRepository<GradingQuestion, Long> {
}
