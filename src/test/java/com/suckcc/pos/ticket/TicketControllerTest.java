package com.suckcc.pos.ticket;

import com.suckcc.pos.auth.PosUserRepository;
import com.suckcc.pos.catalog.Product;
import com.suckcc.pos.catalog.ProductRepository;
import com.suckcc.pos.payment.CheckoutService;
import com.suckcc.pos.payment.PaymentCalculator;
import com.suckcc.pos.payment.PaymentMethod;
import com.suckcc.pos.sale.CartService;
import com.suckcc.pos.shift.ShiftService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TicketControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PosUserRepository users;

	@Autowired
	private ShiftService shiftService;

	@Autowired
	private ProductRepository products;

	@Autowired
	private CartService cartService;

	@Autowired
	private CheckoutService checkoutService;

	@Test
	@WithUserDetails("cajero1")
	void listsSearchesAndRendersPrintReadyReceipt() throws Exception {
		Long cashierId = users.findByUsernameAndActiveTrue("cajero1").orElseThrow().getId();
		shiftService.openShift(cashierId, 50_000L);

		Product cookie = new Product();
		cookie.setSku("GALLETA-01");
		cookie.setName("Galleta Chispas");
		cookie.setPriceCents(3_500L);
		cookie.setTaxRateBps(1600);
		cookie.setStockQty(10);
		cookie.setTrackInventory(true);
		cookie.setActive(true);
		products.save(cookie);

		cartService.addProduct(cashierId, cookie.getId());
		CheckoutService.CheckoutResult checkout = checkoutService.checkoutOpenSale(
				cashierId,
				List.of(
						new PaymentCalculator.TenderInput(PaymentMethod.CARD, 1_500L, "REF-1"),
						new PaymentCalculator.TenderInput(PaymentMethod.CASH, 5_000L, null)
				)
		);

		MvcResult listRes = mockMvc.perform(get("/tickets"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(listRes.getResponse().getContentAsString())
				.contains(checkout.sale().getTicketNumber())
				.contains("$35.00");

		MvcResult searchRes = mockMvc.perform(get("/tickets")
						.param("q", checkout.sale().getTicketNumber())
						.header("HX-Target", "ticket-rows"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(searchRes.getResponse().getContentAsString())
				.contains(checkout.sale().getTicketNumber())
				.doesNotContain("<html");

		MvcResult printRes = mockMvc.perform(get("/tickets/{id}/print", checkout.sale().getId()))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(printRes.getResponse().getContentAsString())
				.contains(checkout.sale().getTicketNumber())
				.contains("Galleta Chispas")
				.contains("CARD (REF-1)")
				.contains("$30.00") // cambio ($65 tendered - $35 total = $30)
				.contains("Cajero Turno 1");
	}
}
