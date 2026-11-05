package com.quizverse.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.quizverse.security.RateLimiter;
import com.quizverse.service.QuizCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Server-side scoring and leaderboards (#21), against the real answer keys built from the question banks. */
@SpringBootTest
@AutoConfigureMockMvc
class ServerScoringTests {

	private static final String QUIZ = "sbi-clerk/prelims/english/hard/3";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private QuizCatalog catalog;

	@Autowired
	private RateLimiter limiter;

	@BeforeEach
	void resetLimits() {
		limiter.clear();
	}

	private MockHttpSession register(String username, String name) throws Exception {
		MockHttpSession session = new MockHttpSession();
		mvc.perform(post("/api/auth/register").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"%s","email":"%s@example.com","displayName":"%s","password":"secret123"}"""
								.formatted(username, username, name)))
				.andExpect(status().isCreated());
		return session;
	}

	private JsonNode call(String path, Object body) throws Exception {
		return json.readTree(mvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(json.writeValueAsString(body)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	/** The first {@code right} answers correct, the rest wrong; null entries skipped from {@code skipFrom}. */
	private List<Integer> chosen(QuizCatalog.Quiz quiz, int right, int skipFrom) {
		List<Integer> key = catalog.answers(quiz);
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < key.size(); i++) {
			out.add(i >= skipFrom ? null : i < right ? key.get(i) : (key.get(i) + 1) % 4);
		}
		return out;
	}

	private ObjectNode attempt(QuizCatalog.Quiz quiz, List<Integer> chosen, String receipt, double claimedScore) {
		String[] p = quiz.key().split("/");
		ObjectNode a = json.createObjectNode();
		a.put("kind", "mock".equals(p[2]) ? "mock" : "exam");
		a.put("examId", p[0]).put("stageId", p[1]).put("sectionId", p[2]).put("levelId", p[3])
				.put("quizIndex", Integer.parseInt(p[4])).put("quizTitle", quiz.title())
				.put("score", claimedScore).put("maxMarks", quiz.maxMarks())
				.put("correct", chosen.size()).put("wrong", 0).put("skipped", 0)
				.put("timed", true).put("timeUsedSeconds", 600);
		if (receipt != null) a.put("receipt", receipt);
		ArrayNode answers = a.putArray("answers");
		List<String> ids = catalog.ids(quiz);
		for (int i = 0; i < ids.size(); i++) {
			ObjectNode r = answers.addObject().put("questionId", ids.get(i)).put("subject", "english")
					.put("topic", "x").put("correct", true).put("seconds", 10);
			if (chosen.get(i) == null) r.putNull("chosen"); else r.put("chosen", chosen.get(i));
		}
		return a;
	}

	private JsonNode save(MockHttpSession session, ObjectNode attempt) throws Exception {
		return json.readTree(mvc.perform(post("/api/attempts").session(session).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(attempt)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	@Test
	void examQuizFilesHaveNoAnswersButTheServerHasTheKey() throws Exception {
		String file = new String(new ClassPathResource("static/data/sbi-clerk/prelims/english/hard-4.json")
				.getInputStream().readAllBytes());
		assertFalse(file.contains("\"answer\""));
		assertFalse(file.contains("\"explanation\""));
		QuizCatalog.Quiz quiz = catalog.find(QUIZ);
		assertEquals(30, quiz.questionCount());
		assertEquals(20, quiz.minutes());
		QuizCatalog.Quiz mock = catalog.find("ibps-clerk/prelims/mock/medium/0");
		assertEquals(100, mock.questionCount());
		assertEquals(3, mock.parts().size());
		// Practice topic pools keep their answers (offline practice).
		String pool = new String(new ClassPathResource("static/data/topics/english/idioms.json").getInputStream().readAllBytes());
		assertTrue(pool.contains("\"answer\""));
	}

	@Test
	void theServerScoresWithNegativeMarkingAndRanksOnlyReceiptedAttempts() throws Exception {
		QuizCatalog.Quiz quiz = catalog.find(QUIZ);
		String start = call("/api/quiz/start", java.util.Map.of("quiz", QUIZ)).get("startToken").asText();

		// 20 right, 6 wrong, 4 skipped: 20 - 6 * 0.25 = 18.5 of 30.
		List<Integer> chosen = chosen(quiz, 20, 26);
		java.util.Map<String, Object> submit = new java.util.HashMap<>();
		submit.put("quiz", QUIZ);
		submit.put("startToken", start);
		submit.put("chosen", chosen);
		JsonNode result = call("/api/quiz/submit", submit);
		assertEquals(18.5, result.at("/result/score").asDouble(), 0.001);
		assertEquals(20, result.at("/result/correct").asInt());
		assertEquals(4, result.at("/result/skipped").asInt());
		assertEquals(30, result.get("answers").size());
		assertTrue(result.get("onTime").asBoolean());
		String receipt = result.get("receipt").asText();
		assertNotNull(receipt);

		// The browser claims 30/30; the server re-scores to 18.5 and ranks it.
		MockHttpSession asha = register("ss_asha", "Asha");
		JsonNode saved = save(asha, attempt(quiz, chosen, receipt, 30));
		assertEquals(18.5, saved.get("score").asDouble(), 0.001);
		assertTrue(saved.get("verified").asBoolean());

		// The same receipt again is history, not a second leaderboard entry.
		assertFalse(save(asha, attempt(quiz, chosen, receipt, 30)).get("verified").asBoolean());

		// A receipt for different answers doesn't verify, and a forged one doesn't either.
		MockHttpSession ravi = register("ss_ravi", "Ravi");
		List<Integer> perfect = chosen(quiz, 30, 30);
		assertFalse(save(ravi, attempt(quiz, perfect, receipt, 30)).get("verified").asBoolean());
		assertFalse(save(ravi, attempt(quiz, perfect, "e30.forged", 30)).get("verified").asBoolean());
		// Still re-scored: a perfect sheet is 30 even without a receipt.
		assertEquals(30, save(ravi, attempt(quiz, perfect, null, 1)).get("score").asDouble(), 0.001);

		// A properly receipted perfect sheet from Ravi.
		String start2 = call("/api/quiz/start", java.util.Map.of("quiz", QUIZ)).get("startToken").asText();
		submit.put("startToken", start2);
		submit.put("chosen", perfect);
		save(ravi, attempt(quiz, perfect, call("/api/quiz/submit", submit).get("receipt").asText(), 30));

		mvc.perform(get("/api/leaderboards/quiz").param("key", QUIZ).session(asha))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.players").value(2))
				.andExpect(jsonPath("$.top[0].name").value("Ravi"))
				.andExpect(jsonPath("$.top[0].score", closeTo(30, 0.001)))
				.andExpect(jsonPath("$.top[1].name").value("Asha"))
				.andExpect(jsonPath("$.top[1].you").value(true))
				.andExpect(jsonPath("$.you.rank").value(2));
		mvc.perform(get("/api/leaderboards/weekly"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.top[0].name").value("Ravi"));

		// Hiding the name.
		mvc.perform(put("/api/me/leaderboard").session(ravi).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"hidden\":true}"))
				.andExpect(jsonPath("$.leaderboardHidden").value(true));
		mvc.perform(get("/api/leaderboards/quiz").param("key", QUIZ))
				.andExpect(jsonPath("$.top[0].name").value("Anonymous"));
	}

	@Test
	void noStartTokenMeansAResultWithoutAReceipt() throws Exception {
		QuizCatalog.Quiz quiz = catalog.find(QUIZ);
		java.util.Map<String, Object> submit = new java.util.HashMap<>();
		submit.put("quiz", QUIZ);
		submit.put("chosen", chosen(quiz, 10, 30));
		JsonNode result = call("/api/quiz/submit", submit);
		assertFalse(result.get("onTime").asBoolean());
		assertTrue(result.get("receipt").isNull());
		// A token for a different quiz doesn't count either.
		submit.put("startToken", call("/api/quiz/start", java.util.Map.of("quiz", "sbi-clerk/prelims/english/hard/0"))
				.get("startToken").asText());
		assertTrue(call("/api/quiz/submit", submit).get("receipt").isNull());

		submit.put("chosen", Arrays.asList(1, 2));
		mvc.perform(post("/api/quiz/submit").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(json.writeValueAsString(submit)))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/quiz/start").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"quiz\":\"nope/x/y/z/0\"}"))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/leaderboards/quiz").param("key", "java/practice/java/x/0")).andExpect(status().isNotFound());
	}

	/** Phase 6: Hindi solutions stay secret until submit; new exams and grouped sets are in the catalog. */
	@Test
	void submitReturnsHindiSolutionsAndNewExamsAreScored() throws Exception {
		String key = "ibps-clerk/prelims/numerical-ability/easy/0";
		QuizCatalog.Quiz quiz = catalog.find(key);
		String file = new String(new ClassPathResource("static/data/" + quiz.parts().get(0).file()).getInputStream().readAllBytes());
		assertTrue(file.contains("\"hi\""), "easy quant quiz has Hindi questions");
		assertFalse(file.contains("\"explanation\""), "no solutions in the public file, in either language");

		ObjectNode req = json.createObjectNode().put("quiz", key);
		ArrayNode chosen = req.putArray("chosen");
		catalog.answers(quiz).forEach(a -> chosen.addNull());
		JsonNode res = call("/api/quiz/submit", req);
		List<String> hi = new ArrayList<>();
		res.get("hiExplanations").forEach(n -> { if (n.isTextual()) hi.add(n.asText()); });
		assertFalse(hi.isEmpty());
		assertTrue(hi.get(0).chars().anyMatch(c -> c >= 0x0900 && c <= 0x097F), "Devanagari text");

		for (String exam : List.of("ibps-po", "sbi-po", "rbi-assistant")) {
			assertNotNull(catalog.find(exam + "/prelims/mock/medium/0"), exam + " has full mocks");
		}
		long grouped = catalog.questions(catalog.find("ibps-po/mains/data-analysis/hard/0")).stream()
				.filter(q -> q.group() != null).count();
		assertEquals(5, grouped);
	}

	@Test
	void formerComingSoonSectionsAreScoredAndInTheMocks() throws Exception {
		// RBI Assistant Mains: Reasoning, English, Numerical Ability, General Awareness and Computer Knowledge.
		QuizCatalog.Quiz mock = catalog.find("rbi-assistant/mains/mock/hard/0");
		assertNotNull(mock);
		List<QuizCatalog.Question> qs = catalog.questions(mock);
		assertEquals(200, qs.size());
		assertEquals(qs.size(), catalog.answers(mock).size());
		List<QuizCatalog.Question> computer = qs.stream().filter(q -> q.subject().equals("computer")).toList();
		assertEquals(40, computer.size());
		assertTrue(computer.stream().allMatch(QuizCatalog.Question::needsReview), "hand-written drafts keep their badge");
		assertTrue(qs.stream().filter(q -> q.subject().equals("quant")).noneMatch(QuizCatalog.Question::needsReview));

		QuizCatalog.Quiz ga = catalog.find("sbi-po/mains/general-economy-banking-awareness/easy/4");
		assertNotNull(ga);
		assertEquals(50, catalog.questions(ga).size());
	}

	@Test
	void appscGroupOnePapersUseOneThirdNegativeMarking() throws Exception {
		QuizCatalog.Quiz paper1 = catalog.find("appsc-group-1/paper-1/mock/medium/2");
		assertNotNull(paper1, "Paper I full mock");
		assertEquals(120, paper1.questionCount());
		assertEquals(120.0, paper1.maxMarks());
		assertEquals(120, paper1.minutes());
		assertNotNull(catalog.find("appsc-group-1/paper-2/mock/hard/0"), "Paper II full mock without current events");

		// One right, one wrong, the rest skipped: 1 - 1/3 per section mark of 1.
		QuizCatalog.Quiz section = catalog.find("appsc-group-1/paper-1/polity/easy/0");
		List<Integer> key = catalog.answers(section);
		List<Integer> chosen = new ArrayList<>(java.util.Collections.nCopies(key.size(), (Integer) null));
		chosen.set(0, key.get(0));
		chosen.set(1, (key.get(1) + 1) % 4);
		assertEquals(0.67, catalog.score(section, chosen).score(), 0.001);

		// Bank exams keep their 1/4.
		QuizCatalog.Quiz bank = catalog.find("ibps-clerk/prelims/english/easy/0");
		List<Integer> bankKey = catalog.answers(bank);
		List<Integer> bankChosen = new ArrayList<>(java.util.Collections.nCopies(bankKey.size(), (Integer) null));
		bankChosen.set(0, (bankKey.get(0) + 1) % 4);
		assertEquals(-0.25, catalog.score(bank, bankChosen).score(), 0.001);
	}
}
