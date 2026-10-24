package com.quizverse.dto;

import com.quizverse.model.QuizAttempt;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class AttemptDtos {

    private AttemptDtos() {}

    private static final String ID = "[a-z0-9-]{1,64}";
    public static final String QUESTION_ID = "q[0-9a-f]{11}";

    /**
     * {@code kind} defaults to "exam"; practice sets send "revise", "bookmarks", "custom", "daily" or "tech-daily".
     * The daily quiz and the daily tech challenge put their IST date (yyyy-mm-dd) in {@code sectionId}. A full mock test ("mock") covers
     * every section of a stage: {@code sectionId} is "mock" and {@code quizIndex} the mock number.
     * {@code challengeCode} is set when the quiz was opened from a friend's challenge link.
     */
    public record AttemptRequest(
            @Pattern(regexp = "exam|revise|bookmarks|custom|daily|tech-daily|mock") String kind,
            @NotBlank @Pattern(regexp = ID) String examId,
            @NotBlank @Pattern(regexp = ID) String stageId,
            @NotBlank @Pattern(regexp = ID) String sectionId,
            @NotBlank @Pattern(regexp = ID) String levelId,
            @Min(0) @Max(100) int quizIndex,
            @NotBlank @Size(max = 200) String quizTitle,
            double score,
            @PositiveOrZero double maxMarks,
            @Min(0) @Max(500) int correct,
            @Min(0) @Max(500) int wrong,
            @Min(0) @Max(500) int skipped,
            boolean timed,
            @Min(0) @Max(36000) int timeUsedSeconds,
            @Size(max = 500) List<@Valid AnswerRequest> answers,
            @Pattern(regexp = "[A-Z0-9]{1,10}") String challengeCode) {}

    /** One question of the attempt. {@code chosen} is null when skipped. */
    public record AnswerRequest(
            @NotBlank @Pattern(regexp = QUESTION_ID) String questionId,
            @NotBlank @Pattern(regexp = ID) String subject,
            @NotBlank @Pattern(regexp = ID) String topic,
            @Min(0) @Max(9) Integer chosen,
            boolean correct,
            @Min(0) @Max(36000) int seconds) {}

    /** Best result per quiz, keyed "exam/stage/section/level/quizIndex" (section "mock" for mocks), for exam.html. */
    public record BestScore(String key, double bestScore, double maxMarks, long attempts) {}

    public record AttemptResponse(Long id, String kind, String examId, String stageId, String sectionId, String levelId,
                                  int quizIndex, String quizTitle, double score, double maxMarks, int correct,
                                  int wrong, int skipped, boolean timed, int timeUsedSeconds, Instant completedAt) {
        public static AttemptResponse of(QuizAttempt a) {
            return new AttemptResponse(a.getId(), a.getKind(), a.getExamId(), a.getStageId(), a.getSectionId(), a.getLevelId(),
                    a.getQuizIndex(), a.getQuizTitle(), a.getScore(), a.getMaxMarks(), a.getCorrect(), a.getWrong(),
                    a.getSkipped(), a.isTimed(), a.getTimeUsedSeconds(), a.getCompletedAt());
        }
    }
}
