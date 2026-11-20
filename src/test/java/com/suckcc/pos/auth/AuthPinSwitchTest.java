package com.suckcc.pos.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthPinSwitchTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void unauthenticatedRequestRedirectsToLogin() throws Exception {
		mockMvc.perform(get("/shifts"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	void loginPageRenders() throws Exception {
		MvcResult result = mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andReturn();

		assertThat(result.getResponse().getContentAsString())
				.contains("Point of Sale")
				.contains("name=\"username\"")
				.contains("name=\"password\"");
	}

	@Test
	void pinSwitchChangesOperatorAndRoleWithoutFullLogout() throws Exception {
		MvcResult loginResult = mockMvc.perform(post("/login")
						.with(csrf())
						.param("username", "admin")
						.param("password", "admin123"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/pos"))
				.andReturn();

		MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
		assertThat(session).isNotNull();

		// Invalid PIN keeps current session and renders inline error
		MvcResult badPin = mockMvc.perform(post("/auth/switch-pin")
						.session(session)
						.with(csrf())
						.header("HX-Request", "true")
						.param("pin", "9999"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(badPin.getResponse().getContentAsString()).contains("PIN inválido");

		// Valid cashier PIN switches principal to Cajero Turno 1 (ROLE_CASHIER)
		MvcResult switched = mockMvc.perform(post("/auth/switch-pin")
						.session(session)
						.with(csrf())
						.header("HX-Request", "true")
						.param("pin", "0000"))
				.andExpect(status().isOk())
				.andExpect(header().string("HX-Refresh", "true"))
				.andReturn();
		assertThat(switched.getResponse().getContentAsString()).contains("Cajero Turno 1");

		// Cashier role is enforced immediately on the same HTTP session
		mockMvc.perform(get("/reports").session(session))
				.andExpect(status().isForbidden());
	}
}
