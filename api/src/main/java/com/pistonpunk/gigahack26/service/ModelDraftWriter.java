package com.pistonpunk.gigahack26.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.pistonpunk.gigahack26.web.dto.OutreachDraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Component
public class ModelDraftWriter implements DraftWriter {

    private static final Logger log = LoggerFactory.getLogger(ModelDraftWriter.class);

    private final ClassifierProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public ModelDraftWriter(ClassifierProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String id() {
        return properties.deepseek().model() + "@draft";
    }

    public boolean available() {
        return properties.deepseek().configured();
    }

    @Override
    public Composed write(Brief brief, List<OutreachDraft.Citation> citations) {
        try {
            String payload = buildPayload(brief);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.deepseek().baseUrl() + "/chat/completions"))
                    .timeout(Duration.ofMillis(properties.deepseek().timeoutMs()))
                    .header("content-type", "application/json")
                    .header("authorization", "Bearer " + properties.deepseek().apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("draft model returned {}", response.statusCode());
                return null;
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                return null;
            }

            JsonNode draft = mapper.readTree(stripFence(content.asText()));
            String subject = draft.path("subject").asText("").trim();
            String body = draft.path("body").asText("").trim();
            if (subject.isBlank() || body.isBlank()) {
                return null;
            }
            return new Composed(subject, TemplateDraftWriter.sanitise(body, brief.voice().avoid()), id());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("draft model call failed: {}", e.toString());
            return null;
        }
    }

    private String buildPayload(Brief brief) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", properties.deepseek().model());
        root.put("temperature", 0.4);
        root.put("max_tokens", 1100);
        root.put("stream", false);
        root.putObject("response_format").put("type", "json_object");

        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", """
                You write first-touch sales email for an IT services firm. You are writing AS the seller.

                You must answer with JSON only: {"subject": "...", "body": "..."}

                The rules that matter:
                - Write like a person, not a brochure. Short sentences. No marketing register.
                - The findings below are the ONLY facts you may rely on. Do not add a fact that is not there: \
                no numbers, no dates, no names, no product capabilities, no claim about their business that the \
                findings do not support. If you are tempted to add colour, cut it instead.
                - NEVER put citation markers, footnote numbers, bracketed references or source names in the body. \
                The reader must never see that we assembled this from documents. Refer to what they have done as \
                something you noticed, in plain speech.
                - Open with what you noticed about them, in one or two sentences. Then one sentence on what that \
                usually means. Then one sentence on what you do about it, drawn from the proposition. Then the ask.
                - No subject line longer than nine words. No exclamation marks. No "I hope this finds you well".
                - The subject is a line a person would actually send, not a headline.""");

        StringBuilder findings = new StringBuilder();
        for (String finding : brief.findings()) {
            findings.append("- ").append(finding).append('\n');
        }
        if (findings.isEmpty()) {
            findings.append("- (nothing specific; keep the note short and ask a genuine question)\n");
        }

        messages.addObject().put("role", "user").put("content", """
                Company: %s
                Service we are selling: %s
                What we do about it, in our words: %s
                Who will read it: %s
                Channel: %s

                What we noticed about them:
                %s
                Voice to write in: %s

                Write the %s now. Answer with JSON only.""".formatted(
                brief.company(),
                brief.serviceLine(),
                brief.proposition(),
                brief.persona(),
                brief.channel(),
                findings,
                brief.voice().describe(),
                brief.channel().equals("inmail") ? "LinkedIn message, under 90 words" : "email"));

        return mapper.writeValueAsString(root);
    }

    private String stripFence(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }
}
