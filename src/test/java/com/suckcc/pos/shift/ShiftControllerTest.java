package com.suckcc.pos.shift;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ShiftControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ShiftService shiftService;

	@Test
	@WithUserDetails("cajero1")
	void openMovementsPreventDuplicateAndCloseCorte() throws Exception {
		// Open shift with $500.00
		mockMvc.perform(post("/shifts/open")
						.with(csrf())
						.param("openingFloat", "500.00"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/shifts?opened"));

		CashShift open = shiftService.findOpenShift().orElseThrow();
		assertThat(open.getOpeningFloatCents()).isEqualTo(50_000L);

		// Second concurrent open shift is rejected
		assertThatThrownBy(() -> shiftService.openShift(open.getOpenedBy(), 10_000L))
				.isInstanceOf(IllegalStateException.class);

		// Record cash IN ($100.00) and OUT ($40.00) via HTMX
		mockMvc.perform(post("/shifts/movements")
						.with(csrf())
						.header("HX-Request", "true")
						.param("type", "IN")
						.param("amount", "100.00")
						.param("reason", "Cambio adicional"))
				.andExpect(status().isOk());

		MvcResult outRes = mockMvc.perform(post("/shifts/movements")
						.with(csrf())
						.header("HX-Request", "true")
						.param("type", "OUT")
						.param("amount", "40.00")
						.param("reason", "Pago garrafones"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(outRes.getResponse().getContentAsString())
				.contains("$560.00")
				.contains("Movimiento registrado");

		// Close shift with $555.00 counted -> -$5.00 variance
		mockMvc.perform(post("/shifts/close")
						.with(csrf())
						.param("countedCash", "555.00")
						.param("notes", "Faltante menor"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/shifts?closed"));

		assertThat(shiftService.findOpenShift()).isEmpty();
		CashShift closed = shiftService.recentShifts().stream()
				.filter(s -> s.getId().equals(open.getId()))
				.findFirst()
				.orElseThrow();
		assertThat(closed.getStatus()).isEqualTo(ShiftStatus.CLOSED);
		assertThat(closed.getExpectedCashCents()).isEqualTo(56_000L);
		assertThat(closed.getCountedCashCents()).isEqualTo(55_500L);
		assertThat(closed.getVarianceCents()).isEqualTo(-500L);

		MvcResult indexAfterClose = mockMvc.perform(get("/shifts"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(indexAfterClose.getResponse().getContentAsString())
				.contains("Abrir turno de caja")
				.contains("-$5.00");
	}
}
