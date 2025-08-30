package com.quizmailer.scheduler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quizmailer.model.Question;
import com.quizmailer.repository.QuestionRepository;
import com.quizmailer.service.EmailService;
import java.util.List;

@Component
public class QuizScheduler {
    private boolean scheduledMailerEnabled=false;
    private static final Logger logger = LoggerFactory.getLogger(QuizScheduler.class);
    
    // Test email for Mailtrap
    private static final String RECIPIENT_EMAIL = "c100711d67-e52a23@inbox.mailtrap.io";

    @Autowired
    private QuestionRepository questionRepo;

    @Autowired
    private EmailService emailService;

    @Scheduled(cron = "0 0 8,20 * * *") // Run at 8AM and 8PM
    public void sendDailyQuiz() {
        if(scheduledMailerEnabled){
        logger.info("Starting quiz scheduler...");
        List<Question> questions = questionRepo.getRandomFiveQuestions();
        
        if (questions.isEmpty()) {
            logger.warn("No questions found in database. Make sure data.sql is properly loaded.");
            return;
        }

        logger.info("Found {} questions", questions.size());
        StringBuilder content = new StringBuilder("Here's your Java quiz:\n\n");
        
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            content.append(i + 1).append(". ").append(q.getQuestionText()).append("\n")
                   .append("   A. ").append(q.getOptionA()).append("\n")
                   .append("   B. ").append(q.getOptionB()).append("\n")
                   .append("   C. ").append(q.getOptionC()).append("\n")
                   .append("   D. ").append(q.getOptionD()).append("\n\n");
        }

        try {
            logger.info("Attempting to send email to {}", RECIPIENT_EMAIL);
            emailService.sendQuizEmail(RECIPIENT_EMAIL, "Your Daily Java Quiz", content.toString());
            logger.info("Quiz email sent successfully!");
        } catch (Exception e) {
            logger.error("Failed to send quiz email: " + e.getMessage(), e);
        }
    }
    }
}
