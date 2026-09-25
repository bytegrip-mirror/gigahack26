package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.Submission;
import com.pistonpunk.gigahack26.domain.SubmissionDetail;
import com.pistonpunk.gigahack26.repository.SubmissionDetailRepository;
import com.pistonpunk.gigahack26.repository.SubmissionRepository;
import com.pistonpunk.gigahack26.web.dto.SubmissionRequest;
import com.pistonpunk.gigahack26.web.dto.SubmissionResponse;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SubmissionService {

    private final SubmissionRepository submissions;
    private final SubmissionDetailRepository details;
    private final Clock clock;

    public SubmissionService(SubmissionRepository submissions, SubmissionDetailRepository details, Clock clock) {
        this.submissions = submissions;
        this.details = details;
        this.clock = clock;
    }

    @Transactional
    public SubmissionResponse create(SubmissionRequest request) {
        Instant now = clock.instant();

        Submission submission = submissions.save(new Submission(
                request.author().trim(),
                request.title().trim(),
                now));

        SubmissionDetail detail = details.save(new SubmissionDetail(
                submission.getId(),
                request.body(),
                request.tags(),
                now));

        return SubmissionResponse.of(submission, detail);
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> list() {
        List<Submission> found = submissions.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        if (found.isEmpty()) {
            return List.of();
        }

        Map<UUID, SubmissionDetail> detailsById = details
                .findBySubmissionIdIn(found.stream().map(Submission::getId).toList())
                .stream()
                .collect(Collectors.toMap(SubmissionDetail::getSubmissionId, Function.identity(), (first, second) -> first));

        return found.stream()
                .map(submission -> SubmissionResponse.of(submission, detailsById.get(submission.getId())))
                .toList();
    }
}
