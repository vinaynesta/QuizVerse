package com.quizverse.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizverse.security.GoogleTokenVerifier;
import com.quizverse.security.GoogleTokenVerifier.GoogleAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Challenge a friend and Google sign-in. Google's token check is mocked: tests can't mint real ID tokens. */
@SpringBootTest
@AutoConfigureMockMvc
class ChallengeAndGoogleTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ObjectMapper json;

	@MockBean
	private GoogleTokenVerifier google;

	@BeforeEach
	void googleOn() {
		when(google.isEnabled()).thenReturn(true);
		when(google.clientId()).thenReturn("test-client.apps.googleusercontent.com");
	}

	private static String quiz(int quizIndex, double score) {
		return """
				{"examId":"sbi-clerk","stageId":"prelims","sectionId":"english","levelId":"medium","quizIndex":%d,
				 "quizTitle":"SBI Clerk - Prelims - English - Medium Quiz %d","score":%s,"maxMarks":30,
				 "correct":20,"wrong":4,"skipped":6,"timed":true,"timeUsedSeconds":1000}"""
				.formatted(quizIndex, quizIndex + 1, score);
	}

	private static String quizViaChallenge(int quizIndex, double score, String code) {
		return quiz(quizIndex, score).replace("\"timeUsedSeconds\":1000", "\"timeUsedSeconds\":1000,\"challengeCode\":\"" + code + "\"");
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

	private JsonNode postJson(MockHttpSession session, String path, String body) throws Exception {
		var req = post(path).session(session).with(csrf());
		if (body != null) req = req.contentType(MediaType.APPLICATION_JSON).content(body);
		return json.readTree(mvc.perform(req).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	@Test
	void friendsJoinTheChallengeBoardWithTheirFirstTry() throws Exception {
		MockHttpSession asha = register("ch_asha", "Asha");
		long attemptId = postJson(asha, "/api/attempts", quiz(2, 21.5)).get("id").asLong();
		JsonNode created = postJson(asha, "/api/challenges/from-attempt/" + attemptId, null);
		String code = created.get("code").asText();
		// One code per attempt: asking again returns the same one.
		org.junit.jupiter.api.Assertions.assertEquals(code,
				postJson(asha, "/api/challenges/from-attempt/" + attemptId, null).get("code").asText());

		MockHttpSession ravi = register("ch_ravi", "Ravi");
		postJson(ravi, "/api/attempts", quizViaChallenge(2, 24.0, code));
		postJson(ravi, "/api/attempts", quizViaChallenge(2, 29.0, code)); // retake: not ranked
		postJson(ravi, "/api/attempts", quizViaChallenge(3, 30.0, code)); // a different quiz: ignored
		postJson(asha, "/api/attempts", quizViaChallenge(2, 30.0, code)); // the creator's retake: ignored

		// Public: a logged-out visitor sees the quiz and the board, with names but no usernames.
		mvc.perform(get("/api/challenges/" + code))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.quiz.examId").value("sbi-clerk"))
				.andExpect(jsonPath("$.quiz.quizIndex").value(2))
				.andExpect(jsonPath("$.creator.name").value("Asha"))
				.andExpect(jsonPath("$.creator.score").value(21.5))
				.andExpect(jsonPath("$.entries", hasSize(1)))
				.andExpect(jsonPath("$.entries[0].name").value("Ravi"))
				.andExpect(jsonPath("$.entries[0].score").value(24.0))
				.andExpect(jsonPath("$.entries[0].you").value(false));
		mvc.perform(get("/api/challenges/" + code).session(ravi))
				.andExpect(jsonPath("$.entries[0].you").value(true))
				.andExpect(jsonPath("$.creator.you").value(false));
	}

	@Test
	void onlyYourOwnExamAttemptsBecomeChallenges() throws Exception {
		MockHttpSession meera = register("ch_meera", "Meera");
		long attemptId = postJson(meera, "/api/attempts", quiz(0, 10)).get("id").asLong();
		MockHttpSession other = register("ch_other", "Other");
		mvc.perform(post("/api/challenges/from-attempt/" + attemptId).session(other).with(csrf()))
				.andExpect(status().isNotFound());
		mvc.perform(post("/api/challenges/from-attempt/" + attemptId).with(csrf()))
				.andExpect(status().isUnauthorized());

		long practiceId = postJson(meera, "/api/attempts", """
				{"kind":"custom","examId":"custom","stageId":"practice","sectionId":"practice","levelId":"practice",
				 "quizIndex":0,"quizTitle":"Custom quiz","score":5,"maxMarks":10,"correct":5,"wrong":5,"skipped":0,
				 "timed":false,"timeUsedSeconds":0}""").get("id").asLong();
		mvc.perform(post("/api/challenges/from-attempt/" + practiceId).session(meera).with(csrf()))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/challenges/NOSUCH12")).andExpect(status().isNotFound());
	}

	@Test
	void googleSignInCreatesAPasswordlessAccountAndSignsBackIn() throws Exception {
		when(google.verify("token-new")).thenReturn(new GoogleAccount("sub-1001", "Lakshmi.R@Gmail.com", true, "Lakshmi R"));
		MockHttpSession session = new MockHttpSession();
		mvc.perform(post("/api/auth/google").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"credential\":\"token-new\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.username").value("lakshmi_r"))
				.andExpect(jsonPath("$.email").value("lakshmi.r@gmail.com"))
				.andExpect(jsonPath("$.hasPassword").value(false))
				.andExpect(jsonPath("$.googleLinked").value(true));
		mvc.perform(get("/api/me").session(session)).andExpect(jsonPath("$.displayName").value("Lakshmi R"));

		// No password yet: password login fails, and Google can't be disconnected.
		mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"login\":\"lakshmi_r\",\"password\":\"\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(delete("/api/me/google").session(session).with(csrf())).andExpect(status().isBadRequest());

		// The first password needs no current one; after that, Google can be disconnected.
		mvc.perform(post("/api/me/password").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"newPassword\":\"lakshmi123\"}"))
				.andExpect(status().isNoContent());
		mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"login\":\"lakshmi_r\",\"password\":\"lakshmi123\"}"))
				.andExpect(status().isOk());

		// Same Google account again: signs in to the same user, even if the Google email changed.
		when(google.verify("token-again")).thenReturn(new GoogleAccount("sub-1001", "new-address@gmail.com", true, "L"));
		mvc.perform(post("/api/auth/google").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"credential\":\"token-again\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("lakshmi_r"));
	}

	@Test
	void googleNeverTakesOverAnExistingPasswordAccount() throws Exception {
		MockHttpSession owner = register("ch_kavya", "Kavya");
		when(google.verify("token-kavya")).thenReturn(new GoogleAccount("sub-2002", "ch_kavya@example.com", true, "Kavya"));
		mvc.perform(post("/api/auth/google").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"credential\":\"token-kavya\"}"))
				.andExpect(status().isConflict());

		// The owner, logged in, connects it; then Google signs them in.
		mvc.perform(post("/api/me/google").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"credential\":\"token-kavya\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.googleLinked").value(true));
		mvc.perform(post("/api/auth/google").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"credential\":\"token-kavya\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("ch_kavya"));

		// A Google account can't be connected to a second user.
		MockHttpSession someoneElse = register("ch_else", "Else");
		mvc.perform(post("/api/me/google").session(someoneElse).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"credential\":\"token-kavya\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void badOrUnverifiedGoogleTokensAreRejected() throws Exception {
		when(google.verify("forged")).thenThrow(new BadJwtException("bad signature"));
		mvc.perform(post("/api/auth/google").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"credential\":\"forged\"}"))
				.andExpect(status().isUnauthorized());
		when(google.verify("unverified")).thenReturn(new GoogleAccount("sub-3003", "x@example.com", false, "X"));
		mvc.perform(post("/api/auth/google").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"credential\":\"unverified\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void googleOnlyAccountDeletesWithUsernameConfirmation() throws Exception {
		when(google.verify("token-del")).thenReturn(new GoogleAccount("sub-4004", "deleteme@gmail.com", true, "Del"));
		MockHttpSession session = new MockHttpSession();
		mvc.perform(post("/api/auth/google").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"credential\":\"token-del\"}"))
				.andExpect(status().isCreated());
		mvc.perform(delete("/api/me").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"confirmUsername\":\"wrong\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(delete("/api/me").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"confirmUsername\":\"deleteme\"}"))
				.andExpect(status().isNoContent());
	}

	@Test
	void statusPublishesTheGoogleClientId() throws Exception {
		mvc.perform(get("/api/status"))
				.andExpect(jsonPath("$.googleClientId").value("test-client.apps.googleusercontent.com"));
	}
}
