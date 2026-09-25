package com.pistonpunk.gigahack26.web;

import com.pistonpunk.gigahack26.service.SubmissionService;
import com.pistonpunk.gigahack26.web.dto.SubmissionRequest;
import com.pistonpunk.gigahack26.web.dto.SubmissionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    private final SubmissionService submissions;

    public SubmissionController(SubmissionService submissions) {
        this.submissions = submissions;
    }

    @GetMapping
    public List<SubmissionResponse> list() {
        return submissions.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubmissionResponse create(@Valid @RequestBody SubmissionRequest request) {
        return submissions.create(request);
    }
}
