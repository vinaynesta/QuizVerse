package com.quizmailer.controller;

import com.datastax.astra.client.collections.commands.cursor.CollectionFindCursor;
import com.datastax.astra.client.collections.definition.documents.Document;
import com.quizmailer.service.AstraQuestionService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
public class QuizController {

    private final AstraQuestionService questionService;

    public QuizController(AstraQuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping("/api/quiz/questions")
    public List<Document> getRandomQuestions(@RequestBody Map<String, String> payload) {
        String questionType = payload.get("questionType");
        List<Document> questions= questionService.getRandomFiveQuestions(questionType);
        System.out.println(questions);
        return questions;
    }

    @PostMapping("/api/quiz/verify")
    public ResponseEntity<Map<String, Object>> verifyAnswer(@RequestBody Map<String, String> payload) {
        String questionId = payload.get("questionId");
        String selectedAnswer = payload.get("selectedAnswer");
        System.out.println("payload : "+ payload);

        List<Document> question = questionService.getQuestionById(questionId);
        if (question.size()==0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Question not found"));
        }

        boolean isCorrect = selectedAnswer.equals(question.get(0).getString("correct_answer"));

        Map<String, Object> response = new HashMap<>();
        response.put("correct", isCorrect);
        response.put("correctAnswer", question.get(0).getString("correct_answer"));

        return ResponseEntity.ok(response);
    }

    // @PostMapping("/api/quiz/addHistory")
    // public ResponseEntity<Map<String, String>> addHistory(@RequestBody Map<String, String> payload) {
    //     String questionId = payload.get("questionId");
    //     String selectedAnswer = payload.get("selectedAnswer");

    //     boolean saved = historyService.saveUserHistory(questionId, selectedAnswer);

    //     if (saved) {
    //         return ResponseEntity.ok(Map.of("status", "History saved successfully"));
    //     } else {
    //         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
    //                 .body(Map.of("error", "Failed to save history"));
    //     }
    // }
}
