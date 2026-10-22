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
	void statusReportsTemporaryStorageOnH2() throws Exception {
		mvc.perform(get("/api/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.temporaryStorage").value(true));
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

	private MockHttpSession login(String login, String password) throws Exception {
		MockHttpSession session = new MockHttpSession();
		mvc.perform(post("/api/auth/login").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"login\":\"%s\",\"password\":\"%s\"}".formatted(login, password)))
				.andExpect(status().isOk());
		return session;
	}

	@Test
	void profileEditsNameAndEmail() throws Exception {
		MockHttpSession session = register("arjun", "arjun@example.com");
		register("taken", "taken@example.com");
		mvc.perform(put("/api/me").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"displayName\":\" Arjun K \",\"email\":\"Arjun.K@Example.com\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("Arjun K"))
				.andExpect(jsonPath("$.email").value("arjun.k@example.com"))
				.andExpect(jsonPath("$.username").value("arjun"));
		// Keeping your own email is fine; taking someone else's is not.
		mvc.perform(put("/api/me").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"displayName\":\"Arjun\",\"email\":\"arjun.k@example.com\"}"))
				.andExpect(status().isOk());
		mvc.perform(put("/api/me").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"displayName\":\"Arjun\",\"email\":\"taken@example.com\"}"))
				.andExpect(status().isConflict());
		mvc.perform(put("/api/me").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"displayName\":\"\",\"email\":\"nope\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(put("/api/me").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"displayName\":\"X\",\"email\":\"x@example.com\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void changingPasswordLogsOutOtherSessions() throws Exception {
		MockHttpSession laptop = register("divya", "divya@example.com");
		MockHttpSession phone = login("divya", "secret123");

		mvc.perform(post("/api/me/password").session(laptop).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"currentPassword\":\"wrong-pass\",\"newPassword\":\"newsecret1\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/me/password").session(laptop).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"currentPassword\":\"secret123\",\"newPassword\":\"short\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/me/password").session(laptop).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"currentPassword\":\"secret123\",\"newPassword\":\"newsecret1\"}"))
				.andExpect(status().isNoContent());

		mvc.perform(get("/api/me").session(laptop)).andExpect(status().isOk());
		org.junit.jupiter.api.Assertions.assertTrue(phone.isInvalid());
		mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"login\":\"divya\",\"password\":\"secret123\"}"))
				.andExpect(status().isUnauthorized());
		login("divya", "newsecret1");
	}

	@Test
	void deletingAccountRemovesUserAndData() throws Exception {
		MockHttpSession session = register("gopal", "gopal@example.com");
		MockHttpSession other = login("gopal@example.com", "secret123");
		saveAttempt(session, attemptWithAnswers(1.75, """
				[{"questionId":"qaaaaaaaaaaa","subject":"reasoning","topic":"syllogism","chosen":1,"correct":true,"seconds":20}]"""));
		mvc.perform(put("/api/me/bookmarks/qaaaaaaaaaaa").session(session).with(csrf())).andExpect(status().isCreated());
		mvc.perform(post("/api/reports").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"questionId\":\"qaaaaaaaaaaa\",\"reason\":\"typo\"}"))
				.andExpect(status().isCreated());

		mvc.perform(delete("/api/me").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"password\":\"wrong-pass\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(delete("/api/me").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"password\":\"secret123\"}"))
				.andExpect(status().isNoContent());

		org.junit.jupiter.api.Assertions.assertTrue(session.isInvalid());
		org.junit.jupiter.api.Assertions.assertTrue(other.isInvalid());
		mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"login\":\"gopal\",\"password\":\"secret123\"}"))
				.andExpect(status().isUnauthorized());
		// The username and email are free again, and the new account starts empty.
		MockHttpSession fresh = register("gopal", "gopal@example.com");
		mvc.perform(get("/api/attempts").session(fresh)).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(get("/api/me/bookmarks").session(fresh)).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void streakDailyGoalAndDailyQuiz() throws Exception {
		MockHttpSession session = register("farah", "farah@example.com");
		String today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).toString();
		mvc.perform(get("/api/me/streak").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.current").value(0))
				.andExpect(jsonPath("$.goal").value(2))
				.andExpect(jsonPath("$.dailyDone").value(false))
				.andExpect(jsonPath("$.date").value(today));

		saveAttempt(session, """
				{"kind":"daily","examId":"daily","stageId":"practice","sectionId":"%s","levelId":"practice",
				 "quizIndex":0,"quizTitle":"Daily quiz","score":7,"maxMarks":10,"correct":7,"wrong":2,"skipped":1,
				 "timed":true,"timeUsedSeconds":420,"answers":[]}""".formatted(today));
		mvc.perform(get("/api/me/streak").session(session))
				.andExpect(jsonPath("$.current").value(1))
				.andExpect(jsonPath("$.today").value(1))
				.andExpect(jsonPath("$.dailyDone").value(true));
		// The daily quiz is practice, so it stays out of the exam badges.
		mvc.perform(get("/api/attempts/best").session(session)).andExpect(jsonPath("$", hasSize(0)));

		mvc.perform(put("/api/me/goal").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"goal\":4}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.goal").value(4));
		mvc.perform(put("/api/me/goal").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"goal\":0}"))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/me/streak")).andExpect(status().isUnauthorized());
	}

	@Test
	void mockTestsGetTheirOwnBestScores() throws Exception {
		MockHttpSession session = register("isha", "isha@example.com");
		String mock = """
				{"kind":"mock","examId":"ibps-clerk","stageId":"prelims","sectionId":"mock","levelId":"easy","quizIndex":2,
				 "quizTitle":"IBPS Clerk - Prelims - Full mock - Easy 3","score":%s,"maxMarks":100,"correct":70,"wrong":20,
				 "skipped":10,"timed":true,"timeUsedSeconds":3400,"answers":[]}""";
		saveAttempt(session, mock.formatted(65));
		saveAttempt(session, mock.formatted(72.5));
		mvc.perform(get("/api/attempts/best").session(session))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].key").value("ibps-clerk/prelims/mock/easy/2"))
				.andExpect(jsonPath("$[0].attempts").value(2))
				.andExpect(jsonPath("$[0].bestScore", closeTo(72.5, 0.001)));
		mvc.perform(get("/api/attempts").session(session)).andExpect(jsonPath("$[0].kind").value("mock"));
		mvc.perform(post("/api/attempts").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(mock.formatted(1).replace("\"mock\",\"examId\"", "\"contest\",\"examId\"")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void logoutEndsTheSession() throws Exception {
		MockHttpSession session = register("lata", "lata@example.com");
		mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
		mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
	}
}
