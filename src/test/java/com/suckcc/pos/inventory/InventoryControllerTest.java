package com.suckcc.pos.inventory;

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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InventoryControllerTest {

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
	@WithUserDetails("admin")
	void recordsSaleLedgerAndAllowsAdminStockAdjustmentsWithHtmxFilter() throws Exception {
		Long adminId = users.findByUsernameAndActiveTrue("admin").orElseThrow().getId();
		shiftService.openShift(adminId, 50_000L);

		Product tea = new Product();
		tea.setSku("TE-VERDE");
		tea.setName("Té Verde");
		tea.setPriceCents(3_000L);
		tea.setTaxRateBps(1600);
		tea.setStockQty(5);
		tea.setTrackInventory(true);
		tea.setActive(true);
		products.save(tea);

		// Sale deducts 1 -> stock 4 and logs SALE (-1)
		cartService.addProduct(adminId, tea.getId());
		checkoutService.checkoutOpenSale(
				adminId,
				List.of(new PaymentCalculator.TenderInput(PaymentMethod.CASH, 3_000L, null))
		);

		// Admin records PURCHASE +10 -> stock 14
		mockMvc.perform(post("/inventory/adjust")
						.with(csrf())
						.param("productId", tea.getId().toString())
						.param("type", "PURCHASE")
						.param("qtyDelta", "10")
						.param("note", "Factura #44"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/inventory"));

		assertThat(products.findById(tea.getId()).orElseThrow().getStockQty()).isEqualTo(14);

		// HTMX filter by product and type=PURCHASE
		MvcResult kardexRes = mockMvc.perform(get("/inventory")
						.param("productId", tea.getId().toString())
						.param("type", "PURCHASE")
						.header("HX-Target", "kardex-rows"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(kardexRes.getResponse().getContentAsString())
				.contains("Factura #44")
				.contains("+10")
				.doesNotContain("Venta ");
	}

	@Test
	@WithUserDetails("cajero1")
	void cashierCanReadInventoryButCannotAdjust() throws Exception {
		mockMvc.perform(get("/inventory"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/inventory/adjust")
						.with(csrf())
						.param("productId", "1")
						.param("type", "PURCHASE")
						.param("qtyDelta", "5"))
				.andExpect(status().isForbidden());
	}
}
