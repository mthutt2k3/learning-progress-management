package com.learning.progress.controller;

import com.learning.progress.service.SubmissionChallengeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/challenge-submissions")
@Tag(name = "Submission Challenge Management", description = "APIs for managing daily challenge submissions")
public class SubmissionChallengeController {

    @Autowired
    private SubmissionChallengeService submissionChallengeService;

}

