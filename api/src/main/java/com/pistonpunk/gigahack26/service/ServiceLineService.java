package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.PolicyQuestion;
import com.pistonpunk.gigahack26.domain.ServiceLine;
import com.pistonpunk.gigahack26.repository.jpa.PolicyQuestionRepository;
import com.pistonpunk.gigahack26.repository.jpa.ServiceLineRepository;
import com.pistonpunk.gigahack26.web.dto.ServiceLineRequest;
import com.pistonpunk.gigahack26.web.dto.ServiceLineView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ServiceLineService {

    private final ServiceLineRepository lines;
    private final PolicyQuestionRepository questions;
    private final Clock clock;

    public ServiceLineService(ServiceLineRepository lines, PolicyQuestionRepository questions, Clock clock) {
        this.lines = lines;
        this.questions = questions;
        this.clock = clock;
    }

    @Transactional
    public void ensureSeeded() {
        List<String> existing = questions.findAll().stream()
                .map(PolicyQuestion::getServiceLine)
                .distinct()
                .toList();
        for (ServiceLineLibrary.Definition definition : ServiceLineLibrary.definitions()) {
            Optional<ServiceLine> found = lines.findByKey(definition.key());
            if (found.isPresent()) {
                fillBlanks(found.get(), definition);
                continue;
            }
            if (!existing.contains(definition.key())) {
                continue;
            }
            lines.save(new ServiceLine(
                    UUID.randomUUID(),
                    definition.key(),
                    definition.name(),
                    definition.description(),
                    definition.valueProposition(),
                    definition.sortOrder(),
                    true,
                    clock.instant()));
        }
    }

    /**
     * A line seeded before it had copy keeps its place and gains the words.
     *
     * The writer falls back to a hardcoded proposition when the line has none,
     * so an empty field is invisible until someone edits it on the Settings
     * screen and finds nothing to edit. Only blanks are filled: a line whose
     * proposition was written by hand is left alone.
     */
    private void fillBlanks(ServiceLine line, ServiceLineLibrary.Definition definition) {
        boolean blankDescription = line.getDescription() == null || line.getDescription().isBlank();
        boolean blankProposition = line.getValueProposition() == null || line.getValueProposition().isBlank();
        if (!blankDescription && !blankProposition) {
            return;
        }
        line.revise(
                line.getName(),
                blankDescription ? definition.description() : line.getDescription(),
                blankProposition ? definition.valueProposition() : line.getValueProposition(),
                line.getSortOrder(),
                line.isActive());
    }

    @Transactional(readOnly = true)
    public List<ServiceLineView> list(boolean includeInactive) {
        List<ServiceLine> found = includeInactive
                ? lines.findAllByOrderBySortOrderAscNameAsc()
                : lines.findByActiveTrueOrderBySortOrderAscNameAsc();
        Map<String, Long> questionCounts = new LinkedHashMap<>();
        for (PolicyQuestion question : questions.findAll()) {
            questionCounts.merge(question.getServiceLine(), 1L, Long::sum);
        }
        return found.stream()
                .map(line -> new ServiceLineView(
                        line.getId(),
                        line.getKey(),
                        line.getName(),
                        line.getDescription(),
                        line.getValueProposition(),
                        line.getSortOrder(),
                        line.isActive(),
                        questionCounts.getOrDefault(line.getKey(), 0L),
                        line.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> activeKeys() {
        return lines.findByActiveTrueOrderBySortOrderAscNameAsc().stream().map(ServiceLine::getKey).toList();
    }

    @Transactional
    public ServiceLineView create(ServiceLineRequest request) {
        String key = request.name().trim();
        if (lines.findByKey(key).isPresent()) {
            throw new IllegalArgumentException("A service line called " + key + " already exists");
        }
        ServiceLine saved = lines.save(new ServiceLine(
                UUID.randomUUID(),
                key,
                key,
                blankToNull(request.description()),
                blankToNull(request.valueProposition()),
                request.sortOrder(),
                request.active(),
                clock.instant()));
        return view(saved);
    }

    @Transactional
    public Optional<ServiceLineView> update(UUID id, ServiceLineRequest request) {
        return lines.findById(id).map(line -> {
            line.revise(
                    request.name().trim(),
                    blankToNull(request.description()),
                    blankToNull(request.valueProposition()),
                    request.sortOrder(),
                    request.active());
            return view(lines.save(line));
        });
    }

    @Transactional
    public void delete(UUID id) {
        ServiceLine line = lines.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No such service line"));
        long attached = questions.findAll().stream()
                .filter(question -> question.getServiceLine().equals(line.getKey()))
                .count();
        if (attached > 0) {
            throw new IllegalStateException(
                    line.getKey() + " still has " + attached + " question(s) attached. Move or delete them first.");
        }
        lines.delete(line);
    }

    @Transactional
    public void rename(String from, String to) {
        lines.findByKey(from).ifPresent(line -> {
            line.revise(to, line.getDescription(), line.getValueProposition(), line.getSortOrder(), line.isActive());
            lines.save(line);
        });
    }

    private ServiceLineView view(ServiceLine line) {
        long attached = questions.findAll().stream()
                .filter(question -> question.getServiceLine().equals(line.getKey()))
                .count();
        return new ServiceLineView(line.getId(), line.getKey(), line.getName(), line.getDescription(),
                line.getValueProposition(), line.getSortOrder(), line.isActive(), attached, line.getCreatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
