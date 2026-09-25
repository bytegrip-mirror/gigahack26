package com.pistonpunk.gigahack26.web;

import com.pistonpunk.gigahack26.service.SubmissionService;
import com.pistonpunk.gigahack26.web.dto.SubmissionRequest;
import com.pistonpunk.gigahack26.web.dto.SubmissionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubmissionControllerTest {

    private SubmissionService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(SubmissionService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SubmissionController(service)).build();
    }

    @Test
    void postReturns201WithTheCreatedSubmission() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any(SubmissionRequest.class))).thenReturn(new SubmissionResponse(
                id, "Ada", "Torch relay", "Runs the demo", List.of("demo"), Instant.parse("2026-09-25T12:00:00Z")));

        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"author":"Ada","title":"Torch relay","body":"Runs the demo","tags":["demo"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.title").value("Torch relay"))
                .andExpect(jsonPath("$.tags[0]").value("demo"));
    }

    @Test
    void postRejectsABlankAuthor() throws Exception {
        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"author":"  ","title":"Torch relay"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postRejectsATitleOverTheColumnLength() throws Exception {
        String tooLong = "t".repeat(161);

        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"Ada\",\"title\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postTrimsNothingItselfAndHandsTheRequestToTheService() throws Exception {
        when(service.create(any(SubmissionRequest.class))).thenReturn(new SubmissionResponse(
                UUID.randomUUID(), "Ada", "Torch relay", null, List.of(), Instant.parse("2026-09-25T12:00:00Z")));

        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"author":"Ada","title":"Torch relay"}
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<SubmissionRequest> captured = ArgumentCaptor.forClass(SubmissionRequest.class);
        verify(service).create(captured.capture());
        assertEquals("Ada", captured.getValue().author());
        assertEquals(List.of(), captured.getValue().tags());
    }

    @Test
    void getReturnsTheList() throws Exception {
        when(service.list()).thenReturn(List.of(new SubmissionResponse(
                UUID.randomUUID(), "Ada", "Torch relay", "Runs the demo", List.of("demo"),
                Instant.parse("2026-09-25T12:00:00Z"))));

        mockMvc.perform(get("/api/submissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].author").value("Ada"));
    }
}
