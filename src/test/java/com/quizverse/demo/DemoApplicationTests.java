package com.quizverse.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DemoApplicationTests {

	@Autowired
	private MockMvc mvc;

	private static final String ATTEMPT = """
			{"examId":"ibps-clerk","stageId":"prelims","sectionId":"english","levelId":"easy","quizIndex":0,
			 "quizTitle":"IBPS Clerk - Prelims - English Language - Easy Quiz 1","score":21.5,"maxMarks":30,
			 "correct":23,"wrong":6,"skipped":1,"timed":true,"timeUsedSeconds":1100}""";

	private MockHttpSession register(String username, String email) throws Exception {
		MockHttpSession session = new MockHttpSession();
		mvc.perform(post("/api/auth/register").session(session).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"%s","email":"%s","displayName":"Asha","password":"secret123"}"""
								.formatted(username, email)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.username").value(username.toLowerCase()));
		return session;
	}

	@Test
	void staticSiteIsPublic() throws Exception {
		mvc.perform(get("/index.html")).andExpect(status().isOk());
		mvc.perform(get("/data/index.json")).andExpect(status().isOk());
	}

	@Test
	void historyNeedsLogin() throws Exception {
		mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/attempts")).andExpect(status().isUnauthorized());
	}

	@Test
	void registerSignsInAndSavesHistory() throws Exception {
		MockHttpSession session = register("Asha_1", "asha1@example.com");
		mvc.perform(get("/api/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("asha1@example.com"));

		mvc.perform(post("/api/attempts").session(session).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(ATTEMPT))
				.andExpect(status().isCreated());
		mvc.perform(get("/api/attempts").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].score").value(21.5));
	}

	@Test
	void loginWithUsernameOrEmail() throws Exception {
		register("ravi", "ravi@example.com");
		for (String login : new String[] {"RAVI", "Ravi@Example.com"}) {
			mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
							.content("{\"login\":\"%s\",\"password\":\"secret123\"}".formatted(login)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.username").value("ravi"));
		}
		mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"login\":\"ravi\",\"password\":\"wrong-pass\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void duplicateAndInvalidSignupsAreRejected() throws Exception {
		register("meena", "meena@example.com");
		mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"MEENA\",\"email\":\"other@example.com\",\"displayName\":\"M\",\"password\":\"secret123\"}"))
				.andExpect(status().isConflict());
		mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"x\",\"email\":\"not-an-email\",\"displayName\":\"M\",\"password\":\"short\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").exists());
	}

	@Test
	void writesWithoutCsrfTokenAreRejected() throws Exception {
		MockHttpSession session = register("kiran", "kiran@example.com");
		mvc.perform(post("/api/attempts").session(session)
						.contentType(MediaType.APPLICATION_JSON).content(ATTEMPT))
				.andExpect(status().isForbidden());
	}

	private static String attemptWithAnswers(double score, String answersJson) {
		return """
				{"examId":"ibps-clerk","stageId":"prelims","sectionId":"reasoning","levelId":"easy","quizIndex":1,
				 "quizTitle":"IBPS Clerk - Prelims - Reasoning Ability - Easy Quiz 2","score":%s,"maxMarks":3,
				 "correct":2,"wrong":1,"skipped":0,"timed":true,"timeUsedSeconds":90,"answers":%s}""".formatted(score, answersJson);
	}

	private void saveAttempt(MockHttpSession session, String body) throws Exception {
		mvc.perform(post("/api/attempts").session(session).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
	}

	@Test
	void answersFeedTopicStatsMistakesAndBestScores() throws Exception {
		MockHttpSession session = register("priya", "priya@example.com");
		saveAttempt(session, attemptWithAnswers(1.75, """
				[{"questionId":"qaaaaaaaaaaa","subject":"reasoning","topic":"syllogism","chosen":1,"correct":true,"seconds":20},
				 {"questionId":"qbbbbbbbbbbb","subject":"reasoning","topic":"syllogism","chosen":0,"correct":false,"seconds":40},
				 {"questionId":"qccccccccccc","subject":"reasoning","topic":"inequality","chosen":null,"correct":true,"seconds":5}]"""));

		mvc.perform(get("/api/me/topics").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[1].topic").value("syllogism"))
				.andExpect(jsonPath("$[1].answered").value(2))
				.andExpect(jsonPath("$[1].accuracy").value(0.5))
				.andExpect(jsonPath("$[1].avgSeconds").value(30.0))
				// A skip sent as "correct" is still counted as not correct.
				.andExpect(jsonPath("$[0].skipped").value(1))
				.andExpect(jsonPath("$[0].correct").value(0));

		mvc.perform(get("/api/me/mistakes").session(session))
				.andExpect(jsonPath("$", hasSize(2)));

		// Getting qbbb right later takes it off the mistakes list.
		saveAttempt(session, attemptWithAnswers(2.5, """
				[{"questionId":"qbbbbbbbbbbb","subject":"reasoning","topic":"syllogism","chosen":2,"correct":true,"seconds":15}]"""));
		mvc.perform(get("/api/me/mistakes").session(session))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].questionId").value("qccccccccccc"));

		mvc.perform(get("/api/attempts/best").session(session))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].key").value("ibps-clerk/prelims/reasoning/easy/1"))
				.andExpect(jsonPath("$[0].attempts").value(2))
				.andExpect(jsonPath("$[0].bestScore", closeTo(2.5, 0.001)));
	}

	@Test
	void practiceAttemptsAreKeptOutOfBestScores() throws Exception {
		MockHttpSession session = register("dev", "dev@example.com");
		saveAttempt(session, """
				{"kind":"revise","examId":"revise","stageId":"practice","sectionId":"practice","levelId":"practice",
				 "quizIndex":0,"quizTitle":"Revise mistakes","score":4,"maxMarks":5,"correct":4,"wrong":1,"skipped":0,
				 "timed":false,"timeUsedSeconds":0,"answers":[]}""");
		mvc.perform(get("/api/attempts").session(session)).andExpect(jsonPath("$[0].kind").value("revise"));
		mvc.perform(get("/api/attempts/best").session(session)).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void bookmarksToggle() throws Exception {
		MockHttpSession session = register("neha", "neha@example.com");
		mvc.perform(put("/api/me/bookmarks/qaaaaaaaaaaa").session(session).with(csrf())).andExpect(status().isCreated());
		mvc.perform(put("/api/me/bookmarks/qaaaaaaaaaaa").session(session).with(csrf())).andExpect(status().isNoContent());
		mvc.perform(get("/api/me/bookmarks").session(session)).andExpect(jsonPath("$", hasSize(1)));
		mvc.perform(delete("/api/me/bookmarks/qaaaaaaaaaaa").session(session).with(csrf())).andExpect(status().isNoContent());
		mvc.perform(get("/api/me/bookmarks").session(session)).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(put("/api/me/bookmarks/not-an-id").session(session).with(csrf())).andExpect(status().isBadRequest());
	}

	@Test
	void reportsAreReviewedByAdminsOnly() throws Exception {
		MockHttpSession user = register("sunil", "sunil@example.com");
		String body = "{\"questionId\":\"qaaaaaaaaaaa\",\"reason\":\"wrong-answer\",\"note\":\"Should be B\"}";
		mvc.perform(post("/api/reports").session(user).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
		mvc.perform(post("/api/reports").session(user).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/admin/reports").session(user)).andExpect(status().isForbidden());

		MockHttpSession admin = register("admin_user", "admin@example.com");
		mvc.perform(get("/api/me").session(admin)).andExpect(jsonPath("$.admin").value(true));
		String id = com.jayway.jsonpath.JsonPath.read(
				mvc.perform(get("/api/admin/reports").session(admin))
						.andExpect(jsonPath("$", hasSize(1)))
						.andExpect(jsonPath("$[0].reportedBy").value("sunil"))
						.andReturn().getResponse().getContentAsString(), "$[0].id").toString();
		mvc.perform(post("/api/admin/reports/" + id + "/resolve").session(admin).with(csrf()))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/admin/reports").session(admin)).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void logoutEndsTheSession() throws Exception {
		MockHttpSession session = register("lata", "lata@example.com");
		mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
		mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
	}
}
