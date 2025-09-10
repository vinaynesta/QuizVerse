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
    private List<Document> questions;

    public QuizController(AstraQuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping("/api/quiz/questions")
    public List<Document> getRandomQuestions(@RequestBody Map<String, String> payload) {
        String questionType = payload.get("questionType");
        String quizNo = payload.get("quizNo");
        questions = questionService.getQuestionsByTypeAndQuiz(questionType, quizNo);
        System.out.println("questions : "+ questions);
        return questions;
    }

    @PostMapping("/api/quiz/verify")
    public ResponseEntity<Map<String, Object>> verifyAnswer(@RequestBody Map<String, String> payload) {
        String currentQuestionNo = payload.get("currentQuestionNo");
        String questionId = payload.get("questionId");
        String selectedAnswer = payload.get("selectedAnswer");
        System.out.println("payload : " + payload);

        Document question = questions.get(Integer.parseInt(currentQuestionNo));

        boolean isCorrect = selectedAnswer.equals(question.getString("correct_answer"));

        Map<String, Object> response = new HashMap<>();
        response.put("correct", isCorrect);
        response.put("correctAnswer", question.getString("correct_answer"));
        response.put("explanation", question.get("explanation"));

        return ResponseEntity.ok(response);
    }
}
