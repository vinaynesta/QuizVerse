package com.quizverse.demo;

import com.quizverse.mail.EmailDigests;
import com.quizverse.mail.Mailer;
import com.quizverse.model.User;
import com.quizverse.repository.QuizAttemptRepository;
import com.quizverse.repository.UserRepository;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reminder and weekly emails. The jobs are called directly with a clock set days ahead, so nothing waits
 * for the real schedule; the mailer is mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EmailDigestTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserRepository users;

	@Autowired
	private QuizAttemptRepository attempts;

	@MockBean
	private Mailer mailer;

	@BeforeEach
	void mailOn() {
		when(mailer.isEnabled()).thenReturn(true);
		when(mailer.send(anyString(), anyString(), anyString(), any())).thenReturn(true);
	}

	private EmailDigests at(Instant now) {
		return new EmailDigests(users, attempts, mailer, Clock.fixed(now, ZoneOffset.UTC), "https://quiz.example");
	}

	private MockHttpSession register(String username, boolean reminders, boolean weekly) throws Exception {
		MockHttpSession session = new MockHttpSession();
		mvc.perform(post("/api/auth/register").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"%s","email":"%s@example.com","displayName":"Mail User","password":"secret123"}"""
								.formatted(username, username)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.emailReminders").value(false));
		mvc.perform(put("/api/me/email-preferences").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"reminders\":%s,\"weekly\":%s}".formatted(reminders, weekly)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.emailReminders").value(reminders))
				.andExpect(jsonPath("$.emailWeekly").value(weekly));
		return session;
	}

	private void finishQuiz(MockHttpSession session) throws Exception {
		mvc.perform(post("/api/attempts").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"examId":"sbi-clerk","stageId":"prelims","sectionId":"english","levelId":"medium","quizIndex":0,
								 "quizTitle":"SBI Clerk - Prelims - English - Medium Quiz 1","score":20,"maxMarks":30,
								 "correct":21,"wrong":4,"skipped":5,"timed":true,"timeUsedSeconds":900}"""))
				.andExpect(status().isCreated());
	}

	@Test
	@SuppressWarnings("unchecked")
	void aReminderGoesOutOncePerQuietSpellWithAWorkingUnsubscribe() throws Exception {
		register("mail_quiet", true, false);
		register("mail_off", false, false);
		Instant now = Instant.now();

		at(now.plus(Duration.ofDays(1))).sendReminders(); // not quiet long enough
		verify(mailer, never()).send(eq("mail_quiet@example.com"), anyString(), anyString(), any());

		at(now.plus(Duration.ofDays(4))).sendReminders();
		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
		verify(mailer).send(eq("mail_quiet@example.com"), anyString(), body.capture(), headers.capture());
		verify(mailer, never()).send(eq("mail_off@example.com"), anyString(), anyString(), any());
		assertTrue(body.getValue().contains("4 days"));
		String oneClick = headers.getValue().get("List-Unsubscribe");
		assertEquals("List-Unsubscribe=One-Click", headers.getValue().get("List-Unsubscribe-Post"));

		at(now.plus(Duration.ofDays(5))).sendReminders(); // same quiet spell: no second reminder
		verify(mailer, times(1)).send(eq("mail_quiet@example.com"), anyString(), anyString(), any());

		// The mail app's one-click POST needs no login or CSRF token.
		String token = oneClick.substring(oneClick.indexOf("token=") + 6, oneClick.length() - 1);
		mvc.perform(post("/api/email/unsubscribe").param("token", token)).andExpect(status().isOk());
		User user = users.findByUsername("mail_quiet").orElseThrow();
		assertFalse(user.isEmailReminders());
		mvc.perform(post("/api/email/unsubscribe").param("token", "bogus")).andExpect(status().isOk());
	}

	@Test
	void theWeeklySummaryCoversActiveUsersOnly() throws Exception {
		MockHttpSession active = register("mail_weekly", false, true);
		register("mail_idle", false, true);
		finishQuiz(active);
		finishQuiz(active);
		clearInvocations(mailer);

		at(Instant.now().plus(Duration.ofDays(1))).sendWeeklySummaries();
		ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(mailer).send(eq("mail_weekly@example.com"), subject.capture(), body.capture(), any());
		verify(mailer, never()).send(eq("mail_idle@example.com"), anyString(), anyString(), any());
		assertEquals("Your QuizVerse week: 2 quizzes", subject.getValue());
		assertTrue(body.getValue().contains("Average score:      67%"), body.getValue());
		assertTrue(body.getValue().contains("unsubscribe.html?token="));

		at(Instant.now().plus(Duration.ofDays(2))).sendWeeklySummaries(); // already sent this week
		verify(mailer, times(1)).send(eq("mail_weekly@example.com"), anyString(), anyString(), any());
	}
}
