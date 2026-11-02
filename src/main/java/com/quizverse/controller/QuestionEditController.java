package com.quizverse.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizverse.dto.AttemptDtos;
import com.quizverse.model.QuestionEdit;
import com.quizverse.repository.QuestionEditRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin question editor (#32), under /api/admin so only admins reach it. Edits are saved here by question id and
 * go live on the next build: download them from {@code GET /api/admin/question-edits/export}, save as
 * scripts/question_edits.json and rerun the build scripts (README, "Question data"). The banks and generators
 * stay the source; an edit is an override on top, so a generator rerun doesn't undo it.
 */
@RestController
@Validated
@RequestMapping("/api/admin/question-edits")
public class QuestionEditController {

    private final QuestionEditRepository edits;
    private final ObjectMapper json;

    public QuestionEditController(QuestionEditRepository edits, ObjectMapper json) {
        this.edits = edits;
        this.json = json;
    }

    /** Null fields keep the bank's value. {@code options} and {@code answer} are given together or not at all. */
    public record EditRequest(
            @Size(min = 1, max = 4000) String question,
            @Size(min = 2, max = 5) List<@Size(min = 1, max = 300) String> options,
            @Min(0) @Max(4) Integer answer,
            @Size(max = 4000) String explanation,
            boolean hidden,
            @Size(max = 500) String note) {}

    public record EditResponse(String questionId, String question, List<String> options, Integer answer,
                               String explanation, boolean hidden, String note, String editedBy, Instant updatedAt) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<EditResponse> list() {
        return edits.findAllByOrderByUpdatedAtDesc().stream().map(this::view).toList();
    }

    @PutMapping("/{questionId}")
    @Transactional
    public ResponseEntity<?> save(@PathVariable @Pattern(regexp = AttemptDtos.QUESTION_ID) String questionId,
                                  @Valid @RequestBody EditRequest req, Principal principal) throws JsonProcessingException {
        if (req.options() != null && req.options().stream().map(String::trim).distinct().count() != req.options().size()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Options must all be different."));
        }
        // Option order differs between quizzes, so an answer index only means something next to its options.
        if ((req.answer() == null) != (req.options() == null)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Change the options and the answer together."));
        }
        if (req.options() != null && req.answer() >= req.options().size()) {
            return ResponseEntity.badRequest().body(Map.of("error", "The answer must be one of the options."));
        }
        QuestionEdit e = edits.findByQuestionId(questionId).orElseGet(QuestionEdit::new);
        e.setQuestionId(questionId);
        e.setQuestion(trimmed(req.question()));
        e.setOptions(req.options() == null ? null : json.writeValueAsString(req.options().stream().map(String::trim).toList()));
        e.setAnswer(req.answer());
        e.setExplanation(trimmed(req.explanation()));
        e.setHidden(req.hidden());
        e.setNote(trimmed(req.note()));
        e.setEditedBy(principal.getName());
        e.setUpdatedAt(Instant.now());
        return ResponseEntity.ok(view(edits.save(e)));
    }

    @DeleteMapping("/{questionId}")
    @Transactional
    public ResponseEntity<Void> discard(@PathVariable @Pattern(regexp = AttemptDtos.QUESTION_ID) String questionId) {
        edits.findByQuestionId(questionId).ifPresent(edits::delete);
        return ResponseEntity.noContent().build();
    }

    /** All edits as scripts/question_edits.json: {"&lt;question id&gt;": {field: value, ...}}, sorted by id. */
    @GetMapping("/export")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Map<String, Object>>> export() {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        edits.findAll().stream()
                .sorted((a, b) -> a.getQuestionId().compareTo(b.getQuestionId()))
                .forEach(e -> {
                    EditResponse v = view(e);
                    Map<String, Object> fields = new LinkedHashMap<>();
                    if (v.question() != null) fields.put("question", v.question());
                    if (v.options() != null) fields.put("options", v.options());
                    if (v.answer() != null) fields.put("answer", v.answer());
                    if (v.explanation() != null) fields.put("explanation", v.explanation());
                    if (v.hidden()) fields.put("hidden", true);
                    out.put(v.questionId(), fields);
                });
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"question_edits.json\"")
                .body(out);
    }

    private EditResponse view(QuestionEdit e) {
        List<String> options = null;
        try {
            if (e.getOptions() != null) options = json.readValue(e.getOptions(), new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Bad options stored for " + e.getQuestionId(), ex);
        }
        return new EditResponse(e.getQuestionId(), e.getQuestion(), options, e.getAnswer(), e.getExplanation(),
                e.isHidden(), e.getNote(), e.getEditedBy(), e.getUpdatedAt());
    }

    private static String trimmed(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
