package com.suckcc.pos.sale;

import com.suckcc.pos.auth.PosUserPrincipal;
import com.suckcc.pos.auth.PosUserRepository;
import com.suckcc.pos.catalog.Product;
import com.suckcc.pos.catalog.ProductRepository;
import com.suckcc.pos.shift.ShiftService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PosCartControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProductRepository products;

	@Autowired
	private ShiftService shiftService;

	@Autowired
	private CartService cartService;

	@Autowired
	private PosUserRepository users;

	@Test
	@WithUserDetails("cajero1")
	void requiresOpenShiftAndSupportsBarcodeScanQtyDiscountAndStockCheck() throws Exception {
		// Without an open shift, /pos prompts to open a shift and scan returns an error toast
		MvcResult closedRes = mockMvc.perform(get("/pos"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(closedRes.getResponse().getContentAsString()).contains("Turno de caja cerrado");

		MvcResult scanWithoutShift = mockMvc.perform(post("/pos/cart/scan")
						.with(csrf())
						.param("code", "LATTE-01"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(scanWithoutShift.getResponse().getContentAsString()).contains("No hay un turno de caja abierto");

		// Open shift + seed active product with stock = 2
		Long cashierId = users.findByUsernameAndActiveTrue("cajero1").orElseThrow().getId();
		shiftService.openShift(cashierId, 50_000L);

		Product latte = new Product();
		latte.setSku("LATTE-01");
		latte.setBarcode("750999000111");
		latte.setName("Latte Vainilla");
		latte.setPriceCents(5_800L);
		latte.setTaxRateBps(1600);
		latte.setStockQty(2);
		latte.setTrackInventory(true);
		latte.setActive(true);
		products.save(latte);

		// Scan barcode -> adds qty 1 ($58.00)
		MvcResult scan1 = mockMvc.perform(post("/pos/cart/scan")
						.with(csrf())
						.param("code", "750999000111"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(scan1.getResponse().getContentAsString())
				.contains("Latte Vainilla")
				.contains("$58.00");

		// Click product -> increments qty to 2 ($116.00)
		mockMvc.perform(post("/pos/cart/items/{id}", latte.getId()).with(csrf()))
				.andExpect(status().isOk());

		CartService.CartView view = cartService.currentCart().orElseThrow();
		assertThat(view.items()).hasSize(1);
		SaleItem item = view.items().getFirst();
		assertThat(item.getQty()).isEqualTo(2);
		assertThat(view.sale().getTotalCents()).isEqualTo(11_600L);

		// Incrementing past stock=2 returns stock error toast without changing qty
		MvcResult overStock = mockMvc.perform(post("/pos/cart/items/{id}/qty", item.getId())
						.with(csrf())
						.param("delta", "1"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(overStock.getResponse().getContentAsString()).contains("Stock insuficiente");
		assertThat(cartService.currentCart().orElseThrow().items().getFirst().getQty()).isEqualTo(2);

		// Apply $16.00 discount -> total becomes $100.00
		MvcResult discountRes = mockMvc.perform(post("/pos/cart/discount")
						.with(csrf())
						.param("discount", "16.00"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(discountRes.getResponse().getContentAsString()).contains("$100.00");

		// Clear cart resets items and totals
		mockMvc.perform(post("/pos/cart/clear").with(csrf()))
				.andExpect(status().isOk());
		CartService.CartView cleared = cartService.currentCart().orElseThrow();
		assertThat(cleared.items()).isEmpty();
		assertThat(cleared.sale().getTotalCents()).isZero();
	}
}
