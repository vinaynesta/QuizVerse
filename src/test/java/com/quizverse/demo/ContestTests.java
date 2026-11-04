package com.quizverse.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizverse.model.Contest;
import com.quizverse.repository.ContestRepository;
import com.quizverse.service.QuizCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Live contests (#22): shuffled papers, server scoring, ranks and percentiles after the end. */
@SpringBootTest
@AutoConfigureMockMvc
class ContestTests {

	private static final String QUIZ = "rrb-clerk/prelims/reasoning/medium/1";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private ContestRepository contests;

	@Autowired
	private QuizCatalog catalog;

	private MockHttpSession register(String username, String name) throws Exception {
		MockHttpSession session = new MockHttpSession();
		mvc.perform(post("/api/auth/register").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"%s","email":"%s@example.com","displayName":"%s","password":"secret123"}"""
								.formatted(username, username, name)))
				.andExpect(status().isCreated());
		return session;
	}

	/** admin_user is the configured admin; another test class may already have created it (shared database). */
	private MockHttpSession adminSession() throws Exception {
		MockHttpSession session = new MockHttpSession();
		int created = mvc.perform(post("/api/auth/register").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"admin_user","email":"admin@example.com","displayName":"Admin","password":"secret123"}"""))
				.andReturn().getResponse().getStatus();
		if (created != 201) {
			mvc.perform(post("/api/auth/login").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
							.content("{\"login\":\"admin_user\",\"password\":\"secret123\"}"))
					.andExpect(status().isOk());
		}
		return session;
	}

	private Contest contest(Instant startsAt, int minutes) {
		Contest c = new Contest();
		c.setTitle("Test contest");
		c.setQuizKey(QUIZ);
		c.setStartsAt(startsAt);
		c.setMinutes(minutes);
		return contests.save(c);
	}

	private JsonNode getJson(MockHttpSession s, String path) throws Exception {
		return json.readTree(mvc.perform(get(path).session(s)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
	}

	/** Answers {@code right} questions correctly by matching the shown options' text to the answer key. */
	private List<Integer> answer(JsonNode paper, int right) throws Exception {
		QuizCatalog.Quiz quiz = catalog.find(QUIZ);
		List<QuizCatalog.Question> qs = catalog.questions(quiz);
		List<Integer> key = catalog.answers(quiz);
		List<Integer> out = new ArrayList<>();
		int done = 0;
		for (JsonNode q : paper.get("questions")) {
			int idx = 0;
			while (!qs.get(idx).id().equals(q.get("id").asText())) idx++;
			String correct = qs.get(idx).options().get(key.get(idx));
			int shown = 0;
			while (!q.get("options").get(shown).asText().equals(correct)) shown++;
			out.add(done++ < right ? shown : null);
		}
		return out;
	}

	private void submit(MockHttpSession s, long id, List<Integer> chosen) throws Exception {
		mvc.perform(post("/api/contests/" + id + "/submit").session(s).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("chosen", chosen))))
				.andExpect(status().isOk());
	}

	@Test
	void aContestRunsFromRegistrationToRanks() throws Exception {
		Contest c = contest(Instant.now().minusSeconds(5), 25);
		long id = c.getId();
		MockHttpSession meena = register("ct_meena", "Meena");
		MockHttpSession arun = register("ct_arun", "Arun");
		MockHttpSession late = register("ct_late", "Late");

		mvc.perform(get("/api/contests/" + id + "/paper").session(meena)).andExpect(status().isForbidden());
		mvc.perform(post("/api/contests/" + id + "/register").with(csrf())).andExpect(status().isUnauthorized());
		for (MockHttpSession s : List.of(meena, arun, late)) {
			mvc.perform(post("/api/contests/" + id + "/register").session(s).with(csrf())).andExpect(status().isOk());
		}
		mvc.perform(get("/api/contests")).andExpect(jsonPath("$[?(@.id == " + id + ")].state").value("live"));

		JsonNode p1 = getJson(meena, "/api/contests/" + id + "/paper");
		JsonNode p2 = getJson(arun, "/api/contests/" + id + "/paper");
		assertEquals(40, p1.get("questions").size());
		// No answers in the paper, and each entrant's order differs.
		assertEquals(false, p1.toString().contains("\"answer\""));
		List<String> order1 = new ArrayList<>();
		List<String> order2 = new ArrayList<>();
		p1.get("questions").forEach(q -> order1.add(q.get("id").asText()));
		p2.get("questions").forEach(q -> order2.add(q.get("id").asText()));
		assertNotEquals(order1, order2);
		// The puzzle set (#19) keeps its 5 questions together and in order, with the shared set-up on each.
		List<QuizCatalog.Question> original = catalog.questions(catalog.find(QUIZ));
		List<String> grouped = original.stream().filter(q -> q.group() != null).map(QuizCatalog.Question::id).toList();
		assertEquals(5, grouped.size());
		List<String> shown = new ArrayList<>();
		p1.get("questions").forEach(q -> shown.add(q.get("id").asText()));
		int at = shown.indexOf(grouped.get(0));
		assertEquals(grouped, shown.subList(at, at + 5));
		assertEquals("Puzzle", p1.at("/questions/" + at + "/group/title").asText());
		// Refreshing gives the same order.
		assertEquals(p1.get("questions").get(0).get("id"), getJson(meena, "/api/contests/" + id + "/paper").at("/questions/0/id"));

		submit(meena, id, answer(p1, 30)); // 30 right, 10 skipped: 30 of 40
		submit(arun, id, answer(p2, 20));
		mvc.perform(post("/api/contests/" + id + "/submit").session(meena).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("chosen", answer(p1, 40)))))
				.andExpect(status().isConflict());

		// Hidden until the contest ends.
		mvc.perform(get("/api/contests/" + id + "/results")).andExpect(status().isForbidden());
		c.setStartsAt(Instant.now().minus(Duration.ofMinutes(30)));
		contests.save(c);
		mvc.perform(get("/api/contests/" + id + "/results").session(arun))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.participants").value(2))
				.andExpect(jsonPath("$.top[0].name").value("Meena"))
				.andExpect(jsonPath("$.top[0].score").value(30.0))
				.andExpect(jsonPath("$.you.rank").value(2))
				.andExpect(jsonPath("$.yourPercentile").value(0.0));
		// After the end: no more papers or submissions.
		mvc.perform(get("/api/contests/" + id + "/paper").session(late)).andExpect(status().isForbidden());
	}

	@Test
	void onlyAdminsScheduleContests() throws Exception {
		String body = """
				{"title":"Sunday mock","quizKey":"ibps-clerk/prelims/mock/easy/0","startsAt":"%s","minutes":0}"""
				.formatted(Instant.now().plus(Duration.ofDays(2)));
		MockHttpSession user = register("ct_user", "User");
		mvc.perform(post("/api/admin/contests").session(user).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isForbidden());
		MockHttpSession admin = adminSession();
		mvc.perform(post("/api/admin/contests").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.minutes").value(60))
				.andExpect(jsonPath("$.questions").value(100))
				.andExpect(jsonPath("$.state").value("upcoming"));
		mvc.perform(post("/api/admin/contests").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(body.replace("ibps-clerk/prelims/mock/easy/0", "java/practice/java/x/0")))
				.andExpect(status().isBadRequest());
	}
}
