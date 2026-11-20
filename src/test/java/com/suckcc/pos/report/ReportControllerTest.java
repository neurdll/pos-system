package com.suckcc.pos.report;

import com.suckcc.pos.auth.PosUserRepository;
import com.suckcc.pos.catalog.Product;
import com.suckcc.pos.catalog.ProductRepository;
import com.suckcc.pos.payment.CheckoutService;
import com.suckcc.pos.payment.PaymentCalculator;
import com.suckcc.pos.payment.PaymentMethod;
import com.suckcc.pos.returns.ReturnService;
import com.suckcc.pos.sale.CartService;
import com.suckcc.pos.sale.Sale;
import com.suckcc.pos.sale.SaleItem;
import com.suckcc.pos.sale.SaleItemRepository;
import com.suckcc.pos.shift.CashShift;
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
class ReportControllerTest {

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

	@Autowired
	private SaleItemRepository saleItems;

	@Autowired
	private ReturnService returnService;

	@Test
	@WithUserDetails("cajero1")
	void cashierIsForbiddenFromReports() throws Exception {
		mockMvc.perform(get("/reports"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithUserDetails("admin")
	void adminReportShowsDailyNetAfterReturnsTendersTopProductsAndLowStock() throws Exception {
		Long adminId = users.findByUsernameAndActiveTrue("admin").orElseThrow().getId();
		CashShift shift = shiftService.openShift(adminId, 50_000L);

		Product espresso = new Product();
		espresso.setSku("ESP-01");
		espresso.setName("Espresso Doble");
		espresso.setPriceCents(4_000L);
		espresso.setTaxRateBps(1600);
		espresso.setStockQty(4);
		espresso.setTrackInventory(true);
		espresso.setActive(true);
		products.save(espresso);

		cartService.addProduct(adminId, espresso.getId());
		cartService.addProduct(adminId, espresso.getId());
		Sale paid = checkoutService.checkoutOpenSale(
				adminId,
				List.of(
						new PaymentCalculator.TenderInput(PaymentMethod.CARD, 5_000L, "C-1"),
						new PaymentCalculator.TenderInput(PaymentMethod.CASH, 3_000L, null)
				)
		).sale();

		// Return 1 unit in cash ($40.00) -> net daily = $40.00, stock becomes 3 (low stock <= 5)
		SaleItem item = saleItems.findBySaleIdOrderByIdAsc(paid.getId()).getFirst();
		returnService.processReturn(
				adminId,
				paid.getId(),
				PaymentMethod.CASH,
				"Cortesía",
				List.of(new ReturnService.ReturnLineRequest(item.getId(), 1, true))
		);

		MvcResult fullRes = mockMvc.perform(get("/reports"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(fullRes.getResponse().getContentAsString())
				.contains("$80.00") // gross sales
				.contains("$40.00") // refunds & net
				.contains("Espresso Doble")
				.contains("Corte X (Abierto)");

		MvcResult htmxRes = mockMvc.perform(get("/reports")
						.param("shiftId", shift.getId().toString())
						.header("HX-Target", "report-content"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(htmxRes.getResponse().getContentAsString())
				.contains("ESP-01")
				.doesNotContain("<html");
	}
}
