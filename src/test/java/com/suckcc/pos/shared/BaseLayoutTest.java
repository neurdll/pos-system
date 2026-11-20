package com.suckcc.pos.shared;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BaseLayoutTest {

	@Autowired
	private TemplateEngine templateEngine;

	@Test
	void rendersHeadWithVendoredAssetsAndCsrfMeta() {
		WebContext ctx = webContext();
		ctx.setVariable("title", "Mostrador");
		ctx.setVariable("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token"));

		String html = templateEngine.process("layout/base", Set.of("head"), ctx);

		assertThat(html).contains("/css/pos.css");
		assertThat(html).contains("/vendor/htmx.min.js");
		assertThat(html).contains("/vendor/alpine.min.js");
		assertThat(html).contains("content=\"test-token\"");
		assertThat(html).contains("htmx:configRequest");
	}

	@Test
	void rendersToastOobFragment() {
		WebContext ctx = webContext();
		ctx.setVariable("message", "Producto agregado");
		ctx.setVariable("kind", "success");

		String html = templateEngine.process("fragments/toast", Set.of("oob"), ctx);

		assertThat(html).contains("hx-swap-oob=\"beforeend:#toast-region\"");
		assertThat(html).contains("toast-success");
		assertThat(html).contains("Producto agregado");
	}

	private static WebContext webContext() {
		JakartaServletWebApplication app = JakartaServletWebApplication.buildApplication(new MockServletContext());
		return new WebContext(app.buildExchange(new MockHttpServletRequest(), new MockHttpServletResponse()));
	}
}
