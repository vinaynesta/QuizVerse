package com.quizverse.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin question editor (#32): admins save edits by question id and export them for the build. */
@SpringBootTest
@AutoConfigureMockMvc
class QuestionEditTests {

	private static final String QID = "q0123456789a";

	@Autowired
	private MockMvc mvc;

	private MockHttpSession login(String username) throws Exception {
		MockHttpSession session = new MockHttpSession();
		int created = mvc.perform(post("/api/auth/register").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"%s","email":"%s@example.com","displayName":"X","password":"secret123"}"""
								.formatted(username, username)))
				.andReturn().getResponse().getStatus();
		if (created != 201) {
			mvc.perform(post("/api/auth/login").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
							.content("{\"login\":\"%s\",\"password\":\"secret123\"}".formatted(username)))
					.andExpect(status().isOk());
		}
		return session;
	}

	@Test
	void adminsEditAndExportQuestions() throws Exception {
		MockHttpSession user = login("qe_user");
		String body = """
				{"question":"  Fixed text?  ","options":["1","2","3","4"],"answer":2,"explanation":"Because.","hidden":false,"note":"typo"}""";
		mvc.perform(put("/api/admin/question-edits/" + QID).session(user).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());

		MockHttpSession admin = login("admin_user");
		mvc.perform(put("/api/admin/question-edits/" + QID).session(admin).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.question").value("Fixed text?"))
				.andExpect(jsonPath("$.editedBy").value("admin_user"));
		// Answer outside the options, duplicate options, or options without an answer are refused.
		for (String bad : new String[] {
				"{\"options\":[\"1\",\"2\"],\"answer\":3,\"hidden\":false}",
				"{\"options\":[\"1\",\"1\"],\"answer\":0,\"hidden\":false}",
				"{\"options\":[\"1\",\"2\"],\"hidden\":false}"}) {
			mvc.perform(put("/api/admin/question-edits/" + QID).session(admin).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isBadRequest());
		}
		mvc.perform(put("/api/admin/question-edits/not-an-id").session(admin).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());

		mvc.perform(get("/api/admin/question-edits/export").session(admin))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"question_edits.json\""))
				.andExpect(jsonPath("$." + QID + ".answer").value(2))
				.andExpect(jsonPath("$." + QID + ".options", hasSize(4)))
				.andExpect(jsonPath("$." + QID + ".hidden").doesNotExist());

		mvc.perform(delete("/api/admin/question-edits/" + QID).session(admin).with(csrf())).andExpect(status().isNoContent());
		mvc.perform(get("/api/admin/question-edits").session(admin))
				.andExpect(jsonPath("$[?(@.questionId == '" + QID + "')]").isEmpty());
	}
}
