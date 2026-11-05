package com.quizverse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The timed exam quizzes and full mocks, with their answer keys, for scoring on the server. Exam quiz files in
 * static/data have no answers; scripts/build_exams.py writes them to answer-keys.json on the classpath, which
 * is not served. Keys look like attempts: "exam/stage/section/level/quizIndex", with section "mock" for a
 * full mock (quiz N of every section of the stage, as quiz.js builds it).
 */
@Service
public class QuizCatalog {

    /**
     * One section of a quiz: its questions, answers and marks. A mock has one part per section. {@code negativeMarking}
     * is the fraction of a question's marks a wrong answer costs: the exam's own value (APPSC 1/3) or the default.
     */
    public record Part(String name, double marks, int minutes, String file, List<String> ids, List<Integer> answers,
                       List<String> explanations, List<String> hiExplanations, double negativeMarking) {}

    public record Quiz(String key, String title, List<Part> parts) {
        public int questionCount() { return parts.stream().mapToInt(p -> p.ids().size()).sum(); }
        public int minutes() { return parts.stream().mapToInt(Part::minutes).sum(); }
        public double maxMarks() { return parts.stream().mapToDouble(Part::marks).sum(); }
    }

    private final Map<String, Quiz> quizzes = new HashMap<>();
    private final double negativeMarking;
    private final ObjectMapper json;

    public QuizCatalog(ObjectMapper json) throws IOException {
        this.json = json;
        JsonNode index = read(json, "static/data/index.json");
        JsonNode keys = read(json, "answer-keys.json");
        this.negativeMarking = keys.path("negativeMarking").asDouble(0.25);
        JsonNode files = keys.path("quizzes");
        for (JsonNode exam : index.path("exams")) {
            double examNegative = exam.path("negativeMarking").asDouble(negativeMarking);
            for (JsonNode stage : exam.path("stages")) {
                Map<String, List<Part>> mockParts = new HashMap<>();
                boolean mockable = stage.path("sections").size() > 1;
                for (JsonNode section : stage.path("sections")) {
                    int minutes = section.path("minutes").asInt(0);
                    if (minutes == 0) mockable = false;
                    for (JsonNode level : section.path("levels")) {
                        JsonNode list = level.path("quizzes");
                        for (int n = 0; n < list.size(); n++) {
                            JsonNode key = files.get(list.get(n).path("file").asText());
                            if (key == null || minutes == 0) continue;
                            Part part = new Part(section.path("name").asText(), section.path("marks").asDouble(),
                                    minutes, list.get(n).path("file").asText(), strings(key.path("ids")), ints(key.path("answers")),
                                    strings(key.path("explanations")), hiStrings(key.path("hiExplanations"),
                                    key.path("ids").size()), examNegative);
                            String id = String.join("/", exam.path("id").asText(), stage.path("id").asText(),
                                    section.path("id").asText(), level.path("id").asText(), String.valueOf(n));
                            String title = String.join(" - ", exam.path("name").asText(), stage.path("name").asText(),
                                    section.path("name").asText(), list.get(n).path("title").asText());
                            quizzes.put(id, new Quiz(id, title, List.of(part)));
                            mockParts.computeIfAbsent(level.path("id").asText() + "/" + n, k -> new ArrayList<>()).add(part);
                        }
                    }
                }
                if (!mockable) continue;
                int sectionCount = stage.path("sections").size();
                mockParts.forEach((levelAndIndex, parts) -> {
                    if (parts.size() != sectionCount) return;
                    String id = String.join("/", exam.path("id").asText(), stage.path("id").asText(), "mock", levelAndIndex);
                    String title = exam.path("name").asText() + " - " + stage.path("name").asText() + " - Full mock";
                    quizzes.put(id, new Quiz(id, title, List.copyOf(parts)));
                });
            }
        }
    }

    /**
     * A question as published (text and options, no answer). {@code group} is the shared DI table, puzzle or
     * passage of a grouped question (the same on every question of the set), or null. {@code needsReview} marks
     * hand-written content not yet fact-checked, shown with a Draft badge.
     */
    public record Question(String id, String subject, String topic, String section, String question, List<String> options,
                           JsonNode group, boolean needsReview) {}

    /** The quiz's questions in order, read from its public files (which have no answers). */
    public List<Question> questions(Quiz quiz) throws IOException {
        List<Question> out = new ArrayList<>();
        for (Part p : quiz.parts()) {
            for (JsonNode q : read(json, "static/data/" + p.file())) {
                out.add(new Question(q.path("id").asText(), q.path("subject").asText(), q.path("topic").asText(),
                        p.name(), q.path("question").asText(), strings(q.path("options")), q.get("group"),
                        q.path("needsReview").asBoolean(false)));
            }
        }
        return out;
    }

    /** All quiz keys, sorted, for the admin's contest form. */
    public List<String> keys() {
        return quizzes.keySet().stream().sorted().toList();
    }

    public Quiz find(String key) {
        return key == null ? null : quizzes.get(key);
    }

    public double negativeMarking() {
        return negativeMarking;
    }

    public static String key(String examId, String stageId, String sectionId, String levelId, int quizIndex) {
        return String.join("/", examId, stageId, sectionId, levelId, String.valueOf(quizIndex));
    }

    /** Per-section and total result for the chosen options (null = skipped), in question order. */
    public record PartScore(String name, double marks, int correct, int wrong, int skipped, double score) {}

    public record Score(double score, double maxMarks, int correct, int wrong, int skipped, List<PartScore> parts,
                        List<Boolean> correctness) {}

    /** Same rules as quiz.js: marks split evenly per section, a wrong answer costs the part's negativeMarking. */
    public Score score(Quiz quiz, List<Integer> chosen) {
        List<PartScore> parts = new ArrayList<>();
        List<Boolean> correctness = new ArrayList<>();
        int i = 0;
        for (Part p : quiz.parts()) {
            double perQuestion = p.marks() / p.ids().size();
            int right = 0, wrong = 0;
            for (int k = 0; k < p.ids().size(); k++, i++) {
                Integer c = chosen.get(i);
                boolean ok = c != null && c.equals(p.answers().get(k));
                correctness.add(ok);
                if (c == null) continue;
                if (ok) right++; else wrong++;
            }
            double s = right * perQuestion - wrong * perQuestion * p.negativeMarking();
            parts.add(new PartScore(p.name(), p.marks(), right, wrong, p.ids().size() - right - wrong, round(s)));
        }
        return new Score(round(parts.stream().mapToDouble(PartScore::score).sum()), quiz.maxMarks(),
                parts.stream().mapToInt(PartScore::correct).sum(), parts.stream().mapToInt(PartScore::wrong).sum(),
                parts.stream().mapToInt(PartScore::skipped).sum(), parts, correctness);
    }

    public List<String> ids(Quiz quiz) {
        return quiz.parts().stream().flatMap(p -> p.ids().stream()).toList();
    }

    public List<Integer> answers(Quiz quiz) {
        return quiz.parts().stream().flatMap(p -> p.answers().stream()).toList();
    }

    public List<String> explanations(Quiz quiz) {
        return quiz.parts().stream().flatMap(p -> p.explanations().stream()).toList();
    }

    /** Hindi explanations (#18), null where a question has no Hindi text. */
    public List<String> hiExplanations(Quiz quiz) {
        return quiz.parts().stream().flatMap(p -> p.hiExplanations().stream()).toList();
    }

    static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static JsonNode read(ObjectMapper json, String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return json.readTree(in);
        }
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return List.copyOf(out);
    }

    /** Hindi explanations, or nulls when the quiz has none (a list that allows nulls, unlike List.copyOf). */
    private static List<String> hiStrings(JsonNode array, int size) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < size; i++) out.add(array.path(i).isTextual() ? array.get(i).asText() : null);
        return java.util.Collections.unmodifiableList(out);
    }

    private static List<Integer> ints(JsonNode array) {
        List<Integer> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asInt()));
        return List.copyOf(out);
    }
}
