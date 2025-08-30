package com.quizmailer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.quizmailer.model.Question;
import com.quizmailer.repository.QuestionRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quiz")
@CrossOrigin(origins = "*")
public class QuizController {
    private ArrayList<Question> history = new ArrayList<>();

    @Autowired
    private QuestionRepository questionRepository;

    @GetMapping("/questions")
    public List<Question> getRandomQuestions() {
        return questionRepository.getRandomFiveQuestions();
    }

    @PostMapping("/verify")
    public Map<String, Object> verifyAnswer(@RequestBody Map<String, String> answer) {
        Question question = questionRepository.findById(Long.parseLong(answer.get("questionId")))
                .orElseThrow(() -> new RuntimeException("Question not found"));
        
        boolean isCorrect = question.getCorrectAnswer().equals(answer.get("selectedAnswer"));
        return Map.of(
            "correct", isCorrect,
            "correctAnswer", question.getCorrectAnswer()
        );
    }

    @PostMapping("/addHistory")
    public void addQuizToHistory(@RequestBody Map<String, String> answer) {
        Question question = questionRepository.findById(Long.parseLong(answer.get("questionId")))
                .orElseThrow(() -> new RuntimeException("Question not found"));
        
        // boolean isCorrect = question.getCorrectAnswer().equals(answer.get("selectedAnswer"));
        // return Map.of(
        //     "correct", isCorrect,
        //     "correctAnswer", question.getCorrectAnswer()
        // );
        history.add(question);
        System.out.println("history : "+history);
    }

    @GetMapping("/history")
    public List<Question> getHistory() {
        return history;
    }
} 