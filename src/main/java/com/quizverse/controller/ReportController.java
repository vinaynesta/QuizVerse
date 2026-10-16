package com.quizverse.controller;

import com.quizverse.dto.AttemptDtos;
import com.quizverse.model.QuestionReport;
import com.quizverse.model.User;
import com.quizverse.repository.QuestionReportRepository;
import com.quizverse.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

/** Users report broken questions; admins (quizverse.admin-usernames) list and resolve them. */
@RestController
@RequestMapping("/api")
public class ReportController {

    private final QuestionReportRepository reports;
    private final UserRepository users;

    public ReportController(QuestionReportRepository reports, UserRepository users) {
        this.reports = reports;
        this.users = users;
    }

    public record ReportRequest(
            @NotBlank @Pattern(regexp = AttemptDtos.QUESTION_ID) String questionId,
            @NotBlank @Pattern(regexp = "wrong-answer|typo|unclear|other") String reason,
            @Size(max = 500) String note) {}

    public record ReportResponse(Long id, String questionId, String reason, String note, String reportedBy,
                                 Instant createdAt) {}

    /** A second report of the same question by the same user, while the first is open, is a no-op. */
    @PostMapping("/reports")
    @Transactional
    public ResponseEntity<Void> report(@Valid @RequestBody ReportRequest req, Principal principal) {
        User user = users.findByUsername(principal.getName()).orElseThrow();
        if (reports.existsByUserIdAndQuestionIdAndResolvedFalse(user.getId(), req.questionId())) {
            return ResponseEntity.noContent().build();
        }
        QuestionReport r = new QuestionReport();
        r.setUser(user);
        r.setQuestionId(req.questionId());
        r.setReason(req.reason());
        String note = req.note() == null ? "" : req.note().trim();
        r.setNote(note.isEmpty() ? null : note);
        reports.save(r);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/admin/reports")
    @Transactional(readOnly = true)
    public List<ReportResponse> open() {
        return reports.findOpen().stream()
                .map(r -> new ReportResponse(r.getId(), r.getQuestionId(), r.getReason(), r.getNote(),
                        r.getUser().getUsername(), r.getCreatedAt()))
                .toList();
    }

    @PostMapping("/admin/reports/{id}/resolve")
    @Transactional
    public ResponseEntity<Void> resolve(@PathVariable Long id) {
        return reports.findById(id)
                .map(r -> {
                    r.setResolved(true);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
