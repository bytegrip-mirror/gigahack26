package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.Submission;
import com.pistonpunk.gigahack26.domain.SubmissionDetail;
import com.pistonpunk.gigahack26.repository.SubmissionDetailRepository;
import com.pistonpunk.gigahack26.repository.SubmissionRepository;
import com.pistonpunk.gigahack26.web.dto.SubmissionRequest;
import com.pistonpunk.gigahack26.web.dto.SubmissionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubmissionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

    private SubmissionRepository submissions;
    private SubmissionDetailRepository details;
    private SubmissionService service;

    @BeforeEach
    void setUp() {
        submissions = mock(SubmissionRepository.class);
        details = mock(SubmissionDetailRepository.class);
        service = new SubmissionService(submissions, details, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createWritesTheRowToPostgresAndTheDocumentToMongo() {
        UUID id = UUID.randomUUID();
        when(submissions.save(any(Submission.class))).thenAnswer(invocation -> {
            Submission toSave = invocation.getArgument(0);
            ReflectionTestUtils.setField(toSave, "id", id);
            return toSave;
        });
        when(details.save(any(SubmissionDetail.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubmissionResponse created = service.create(new SubmissionRequest(
                "  Ada  ", "  Torch relay  ", "Runs the demo on stage", List.of("demo")));

        assertEquals(id, created.id());
        assertEquals("Ada", created.author());
        assertEquals("Torch relay", created.title());
        assertEquals("Runs the demo on stage", created.body());
        assertEquals(List.of("demo"), created.tags());
        assertEquals(NOW, created.createdAt());

        ArgumentCaptor<SubmissionDetail> savedDetail = ArgumentCaptor.forClass(SubmissionDetail.class);
        verify(details).save(savedDetail.capture());
        assertEquals(id, savedDetail.getValue().getSubmissionId());
        assertEquals(NOW, savedDetail.getValue().getCreatedAt());
    }

    @Test
    void createWithoutTagsStoresAnEmptyTagList() {
        when(submissions.save(any(Submission.class))).thenAnswer(invocation -> {
            Submission toSave = invocation.getArgument(0);
            ReflectionTestUtils.setField(toSave, "id", UUID.randomUUID());
            return toSave;
        });
        when(details.save(any(SubmissionDetail.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubmissionResponse created = service.create(new SubmissionRequest("Ada", "Torch relay", null, null));

        assertEquals(List.of(), created.tags());
        assertNull(created.body());
    }

    @Test
    void listMergesThePostgresRowWithItsMongoDocument() {
        Submission withDetail = submission(UUID.randomUUID(), "Ada");
        Submission withoutDetail = submission(UUID.randomUUID(), "Grace");
        when(submissions.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(withDetail, withoutDetail));
        when(details.findBySubmissionIdIn(anyCollection()))
                .thenReturn(List.of(new SubmissionDetail(withDetail.getId(), "Body", List.of("ml"), NOW)));

        List<SubmissionResponse> listed = service.list();

        assertEquals(2, listed.size());
        assertEquals("Body", listed.get(0).body());
        assertEquals(List.of("ml"), listed.get(0).tags());
        assertNull(listed.get(1).body());
        assertEquals(List.of(), listed.get(1).tags());
    }

    @Test
    void listSkipsTheMongoQueryWhenPostgresIsEmpty() {
        when(submissions.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of());

        assertEquals(List.of(), service.list());
    }

    private Submission submission(UUID id, String author) {
        Submission submission = new Submission(author, "Torch relay", NOW);
        ReflectionTestUtils.setField(submission, "id", id);
        return submission;
    }
}
