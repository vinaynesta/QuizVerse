package com.quizverse.controller;

import com.quizverse.dto.AttemptDtos;
import com.quizverse.model.Bookmark;
import com.quizverse.model.User;
import com.quizverse.repository.BookmarkRepository;
import com.quizverse.repository.UserRepository;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Saved questions. PUT and DELETE are idempotent, so the star can be toggled freely. */
@RestController
@Validated
@RequestMapping("/api/me/bookmarks")
public class BookmarkController {

    static final int MAX_BOOKMARKS = 2000;

    private final BookmarkRepository bookmarks;
    private final UserRepository users;

    public BookmarkController(BookmarkRepository bookmarks, UserRepository users) {
        this.bookmarks = bookmarks;
        this.users = users;
    }

    public record BookmarkResponse(String questionId, Instant createdAt) {}

    /** Newest first. */
    @GetMapping
    public List<BookmarkResponse> list(Principal principal) {
        return bookmarks.findByUserIdOrderByCreatedAtDesc(user(principal).getId()).stream()
                .map(b -> new BookmarkResponse(b.getQuestionId(), b.getCreatedAt()))
                .toList();
    }

    @PutMapping("/{questionId}")
    @Transactional
    public ResponseEntity<?> add(@PathVariable @Pattern(regexp = AttemptDtos.QUESTION_ID) String questionId,
                                 Principal principal) {
        User user = user(principal);
        if (bookmarks.findByUserIdAndQuestionId(user.getId(), questionId).isPresent()) {
            return ResponseEntity.noContent().build();
        }
        if (bookmarks.countByUserId(user.getId()) >= MAX_BOOKMARKS) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "You can keep up to " + MAX_BOOKMARKS + " bookmarks."));
        }
        Bookmark b = new Bookmark();
        b.setUser(user);
        b.setQuestionId(questionId);
        bookmarks.save(b);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{questionId}")
    @Transactional
    public ResponseEntity<Void> remove(@PathVariable @Pattern(regexp = AttemptDtos.QUESTION_ID) String questionId,
                                       Principal principal) {
        bookmarks.findByUserIdAndQuestionId(user(principal).getId(), questionId).ifPresent(bookmarks::delete);
        return ResponseEntity.noContent().build();
    }

    private User user(Principal principal) {
        return users.findByUsername(principal.getName()).orElseThrow();
    }
}
