package com.pistonpunk.gigahack26.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.pistonpunk.gigahack26.domain.PolicyQuestion;
import com.pistonpunk.gigahack26.domain.Verdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class DeepSeekEngine implements ClassifierEngine {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekEngine.class);
    private static final int MAX_DOCUMENT_CHARS = 9000;

    private final ClassifierProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final java.util.concurrent.Semaphore permits;
    private final java.util.concurrent.atomic.AtomicLong calls = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong struck = new java.util.concurrent.atomic.AtomicLong();

    public long callCount() {
        return calls.get();
    }

    public long strikeCount() {
        return struck.get();
    }

    public void resetCounters() {
        calls.set(0);
        struck.set(0);
    }

    public DeepSeekEngine(ClassifierProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.permits = new java.util.concurrent.Semaphore(Math.max(1, properties.deepseek().concurrency()));
    }

    @Override
    public String id() {
        return properties.deepseek().model() + "@deepseek";
    }

    @Override
    public boolean available() {
        return properties.deepseek().configured();
    }

    @Override
    public List<Finding> classify(ClassificationRequest request) {
        if (!available()) {
            return List.of();
        }

        String document = request.document();
        if (document == null || document.isBlank()) {
            return List.of();
        }
        if (document.length() > MAX_DOCUMENT_CHARS) {
            document = document.substring(0, MAX_DOCUMENT_CHARS);
        }

        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }

        try {
            calls.incrementAndGet();
            String payload = buildPayload(request, document);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(properties.deepseek().baseUrl() + "/chat/completions"))
                    .timeout(Duration.ofMillis(properties.deepseek().timeoutMs()))
                    .header("content-type", "application/json")
                    .header("authorization", "Bearer " + properties.deepseek().apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = send(httpRequest);
            if (response.statusCode() != 200) {
                log.warn("deepseek returned {}: {}", response.statusCode(), summarise(response.body()));
                throw new LanguageModelUnavailableException("model returned " + response.statusCode());
            }
            return parse(response.body(), request, document);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LanguageModelUnavailableException("interrupted", e);
        } catch (LanguageModelUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("deepseek call failed: {}", e.toString());
            throw new LanguageModelUnavailableException(e.toString(), e);
        } finally {
            permits.release();
        }
    }

    private static final int MAX_ATTEMPTS = 5;
    private static final long BASE_BACKOFF_MS = 400;

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        HttpResponse<String> response = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status != 429 && status != 402 && status != 503) {
                return response;
            }
            long wait = BASE_BACKOFF_MS * (1L << attempt);
            log.debug("model asked us to slow down ({}); waiting {} ms", status, wait);
            Thread.sleep(wait);
        }
        return response;
    }

    private String buildPayload(ClassificationRequest request, String document) throws Exception {
        PolicyQuestion question = request.question();
        ObjectNode root = mapper.createObjectNode();
        root.put("model", properties.deepseek().model());
        root.put("temperature", 0);
        root.put("max_tokens", 1800);
        root.put("stream", false);

        ObjectNode responseFormat = root.putObject("response_format");
        responseFormat.put("type", "json_object");

        ArrayNode messages = root.putArray("messages");
        messages.addObject()
                .put("role", "system")
                .put("content", """
                        You are a sales-signal classifier. You decide whether one document is evidence \
                        about one company for one configured buying-signal question.

                        You must answer with JSON only:
                        {"match": true|false, "confidence": 0.0-1.0, "quote": "...", "verdict": "BUY|COUNTER|BUILD", "reason": "..."}

                        Rules that decide the score:
                        - "quote" MUST be copied character-for-character from the document. It is checked by exact \
                        containment. If you cannot copy an exact substring, return match=false. Never paraphrase, \
                        never correct spelling, never join fragments from different places.
                        - Keep "quote" under 240 characters. Take the shortest run of sentences that carries the \
                        evidence; a long quote is truncated in transmission and the whole answer is then discarded.
                        - Keep "reason" under 20 words. Answer with the JSON object and nothing after it.
                        - "confidence" is your probability that a sales manager would act on this document for this \
                        question. Use 0.5 when the document is genuinely ambiguous, and below 0.5 when it is not \
                        evidence. Do not inflate.
                        - verdict BUY: the document shows the company needs this service from an outside provider.
                        - verdict COUNTER: the document shows something that argues against buying (a hiring freeze, \
                        budget cuts, a signed competitor contract, a pause).
                        - verdict BUILD is narrow: the company SELLS or provides this capability to others — a vendor, \
                        a consultancy or a competitor — and the document is about its own product or service. \
                        A company that uses, buys, hires for or runs this capability internally is NOT BUILD: an internal \
                        data team or AI in production is the normal state of a large company and is often exactly the \
                        buying signal the question asks for.
                        - The question is scoped to ONE company. Evidence about a different company, a customer, or a \
                        partner is not evidence about this company. Return match=false.
                        - A vendor's own marketing copy is not evidence about the vendor being a buyer.""");

        messages.addObject()
                .put("role", "user")
                .put("content", """
                        COMPANY: %s

                        QUESTION: %s

                        RUBRIC (this defines what counts and what does not):
                        %s

                        DOCUMENT:
                        ---
                        %s
                        ---

                        Answer with JSON only.""".formatted(
                        request.companyName(),
                        question.getText(),
                        question.getInstructions(),
                        document));

        return mapper.writeValueAsString(root);
    }

    private List<Finding> parse(String body, ClassificationRequest request, String document) throws Exception {
        JsonNode root = mapper.readTree(body);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new LanguageModelUnavailableException("empty completion");
        }

        JsonNode verdictNode = readJson(stripFence(content.asText()));
        if (verdictNode == null) {
            throw new LanguageModelUnavailableException("unparseable completion");
        }
        if (!verdictNode.path("match").asBoolean(false)) {
            return List.of();
        }

        double confidence = verdictNode.path("confidence").asDouble(0.0);
        if (confidence <= properties.confidenceFloor() || confidence > 1.0) {
            return List.of();
        }

        String quote = verdictNode.path("quote").asText("");
        Optional<SpanLocator.Span> span = SpanLocator.locate(document, quote);
        if (span.isEmpty()) {
            struck.incrementAndGet();
            log.info("struck a row: quote not found in document for question {}", request.question().getKey());
            return List.of();
        }

        Verdict verdict = Verdict.parse(verdictNode.path("verdict").asText(null));
        if (verdict == Verdict.NONE) {
            verdict = switch (request.question().getPolarity()) {
                case POSITIVE -> Verdict.BUY;
                case NEGATIVE -> Verdict.COUNTER;
                case DISQUALIFYING -> Verdict.BUILD;
            };
        }

        List<Finding> findings = new ArrayList<>();
        findings.add(new Finding(
                verdict,
                confidence,
                span.get().start(),
                span.get().end(),
                verdictNode.path("reason").asText(null)));
        return findings;
    }

    private JsonNode readJson(String raw) {
        try {
            return mapper.readTree(raw);
        } catch (Exception e) {
            String cleaned = raw.replaceAll("[\\u0000-\\u001F&&[^\\n\\r\\t]]", " ");
            try {
                return mapper.readTree(cleaned);
            } catch (Exception ignored) {
                return salvage(raw);
            }
        }
    }

    private JsonNode salvage(String raw) {
        {
            int quoteStart = raw.indexOf("\"quote\"");
            if (quoteStart < 0) {
                return null;
            }
            java.util.regex.Matcher quoted = java.util.regex.Pattern
                    .compile("\"quote\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(raw);
            if (!quoted.find()) {
                return null;
            }
            tools.jackson.databind.node.ObjectNode salvaged = mapper.createObjectNode();
            salvaged.put("match", true);
            double confidence = 0.7;
            java.util.regex.Matcher level = java.util.regex.Pattern
                    .compile("\"confidence\"\\s*:\\s*([0-9.]+)").matcher(raw);
            if (level.find()) {
                try {
                    confidence = Double.parseDouble(level.group(1));
                } catch (NumberFormatException ignored) {
                    confidence = 0.7;
                }
            }
            salvaged.put("confidence", confidence);
            salvaged.put("quote", quoted.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
            java.util.regex.Matcher verdict = java.util.regex.Pattern
                    .compile("\"verdict\"\\s*:\\s*\"([A-Za-z_]+)\"").matcher(raw);
            salvaged.put("verdict", verdict.find() ? verdict.group(1) : "NONE");
            salvaged.put("reason", "recovered from a truncated answer");
            return salvaged;
        }
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

    private String summarise(String body) {
        return body == null ? "" : body.substring(0, Math.min(200, body.length()));
    }
}
