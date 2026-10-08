package com.quizverse.controller;

import com.datastax.astra.client.collections.definition.documents.Document;
import com.quizverse.service.AstraQuestionService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class QuizController {

    private final AstraQuestionService questionService;

    public QuizController(AstraQuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping("/api/quiz/questions")
    public List<Document> getRandomQuestions(@RequestBody Map<String, String> payload) {
        String questionType = payload.get("questionType");
        String quizNo = payload.get("quizNo");
        List<Document> questions = questionService.getQuestionsByTypeAndQuiz(questionType, quizNo);
        // Never send answers to the client; they are checked in /verify.
        questions.forEach(q -> {
            q.remove("correct_answer");
            q.remove("explanation");
        });
        return questions;
    }

    @PostMapping("/api/quiz/verify")
    public ResponseEntity<Map<String, Object>> verifyAnswer(@RequestBody Map<String, String> payload) {
        String questionId = payload.get("questionId");
        String selectedAnswer = payload.get("selectedAnswer");
        if (questionId == null || selectedAnswer == null) {
            return ResponseEntity.badRequest().build();
        }

        List<Document> found = questionService.getQuestionById(questionId);
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Document question = found.get(0);
        String correctAnswer = question.getString("correct_answer");

        Map<String, Object> response = new HashMap<>();
        response.put("correct", selectedAnswer.equals(correctAnswer));
        response.put("correctAnswer", correctAnswer);
        response.put("explanation", question.get("explanation"));

        return ResponseEntity.ok(response);
    }
}
