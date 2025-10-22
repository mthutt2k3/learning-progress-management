package com.learning.progress.service.strategy;

import com.learning.progress.exception.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QuestionStrategyFactory {

    private final List<QuestionHandleStrategy> strategies;

    @Autowired
    public QuestionStrategyFactory(List<QuestionHandleStrategy> strategies) {
        this.strategies = strategies;
    }

    public QuestionHandleStrategy getStrategy(String questionType) {
        return strategies.stream()
                .filter(strategy -> strategy.supports(questionType))
                .findFirst()
                .orElseThrow(() -> new ApiException("Unsupported question type: " + questionType, HttpStatus.BAD_REQUEST.value()));
    }
}
