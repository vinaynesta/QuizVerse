package com.quizverse.demo;

import com.quizverse.mail.Mailer;
import com.quizverse.security.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Forgot password. The mailer is mocked so the test can read the link out of the "sent" email. */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private RateLimiter limiter;

	@MockBean
	private Mailer mailer;

	@BeforeEach
	void resetLimits() {
		limiter.clear();
	}

	private MockHttpSession register(String username) throws Exception {
		MockHttpSession session = new MockHttpSession();
		mvc.perform(post("/api/auth/register").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"%s","email":"%s@example.com","displayName":"Reset User","password":"oldpass123"}"""
								.formatted(username, username)))
				.andExpect(status().isCreated());
		return session;
	}

	private void requestReset(String email) throws Exception {
		mvc.perform(post("/api/auth/password-reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").exists());
	}

	private String tokenFromEmail(String to) {
		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(mailer, org.mockito.Mockito.atLeastOnce()).send(eq(to), anyString(), body.capture());
		Matcher m = Pattern.compile("reset-password\\.html\\?token=([A-Za-z0-9_-]+)").matcher(body.getValue());
		assertTrue(m.find(), "email contains the reset link");
		return m.group(1);
	}

	private org.springframework.test.web.servlet.ResultActions confirm(String token, String password) throws Exception {
		return mvc.perform(post("/api/auth/password-reset/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\"}"));
	}

	private org.springframework.test.web.servlet.ResultActions login(String name, String password) throws Exception {
		return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"login\":\"" + name + "\",\"password\":\"" + password + "\"}"));
	}

	@Test
	void theEmailedLinkSetsANewPasswordOnceAndLogsOutEverySession() throws Exception {
		MockHttpSession oldSession = register("pr_anil");
		requestReset("PR_Anil@Example.com ");
		String token = tokenFromEmail("pr_anil@example.com");

		confirm(token, "short").andExpect(status().isBadRequest());
		confirm(token, "newpass456").andExpect(status().isNoContent());

		login("pr_anil", "oldpass123").andExpect(status().isUnauthorized());
		login("pr_anil", "newpass456").andExpect(status().isOk());
		mvc.perform(get("/api/me").session(oldSession)).andExpect(status().isUnauthorized());

		// Single use.
		confirm(token, "another789").andExpect(status().isBadRequest());
	}

	@Test
	void anUnknownEmailGetsTheSameAnswerAndNoEmail() throws Exception {
		requestReset("nobody-here@example.com");
		verify(mailer, never()).send(eq("nobody-here@example.com"), anyString(), anyString());
		verify(mailer, never()).send(anyString(), anyString(), anyString(), any());
		confirm("not-a-real-token", "newpass456").andExpect(status().isBadRequest());
	}

	@Test
	void aNewResetCancelsOlderLinks() throws Exception {
		register("pr_bina");
		requestReset("pr_bina@example.com");
		String first = tokenFromEmail("pr_bina@example.com");
		requestReset("pr_bina@example.com");
		String second = tokenFromEmail("pr_bina@example.com");
		confirm(second, "newpass456").andExpect(status().isNoContent());
		confirm(first, "newpass789").andExpect(status().isBadRequest());
	}

	@Test
	void requestsPerEmailAreRateLimited() throws Exception {
		for (int i = 0; i < 3; i++) requestReset("pr_spam@example.com");
		mvc.perform(post("/api/auth/password-reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"pr_spam@example.com\"}"))
				.andExpect(status().isTooManyRequests());
	}
}
