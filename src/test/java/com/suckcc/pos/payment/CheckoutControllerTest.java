package com.suckcc.pos.payment;

import com.suckcc.pos.auth.PosUserRepository;
import com.suckcc.pos.catalog.Product;
import com.suckcc.pos.catalog.ProductRepository;
import com.suckcc.pos.sale.CartService;
import com.suckcc.pos.sale.Sale;
import com.suckcc.pos.sale.SaleRepository;
import com.suckcc.pos.sale.SaleStatus;
import com.suckcc.pos.shift.CashShift;
import com.suckcc.pos.shift.ShiftService;
import com.suckcc.pos.shift.ShiftSummary;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CheckoutControllerTest {

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
	private SaleRepository sales;

	@Autowired
	private PaymentRepository payments;

	@Test
	@WithUserDetails("cajero1")
	void splitPaymentMarksSalePaidDeductsStockAndUpdatesShiftCashSales() throws Exception {
		Long cashierId = users.findByUsernameAndActiveTrue("cajero1").orElseThrow().getId();
		CashShift shift = shiftService.openShift(cashierId, 50_000L);

		Product mug = new Product();
		mug.setSku("TAZA-01");
		mug.setName("Taza Cerámica");
		mug.setPriceCents(10_000L);
		mug.setTaxRateBps(1600);
		mug.setStockQty(5);
		mug.setTrackInventory(true);
		mug.setActive(true);
		products.save(mug);

		cartService.addProduct(cashierId, mug.getId());
		Sale openSale = cartService.currentCart().orElseThrow().sale();

		// Open checkout modal
		MvcResult modalRes = mockMvc.perform(get("/pos/checkout"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(modalRes.getResponse().getContentAsString()).contains("$100.00");

		// Underpayment triggers HTMX retarget to #checkout-error without closing sale
		MvcResult underpay = mockMvc.perform(post("/pos/checkout")
						.with(csrf())
						.param("method", "CASH")
						.param("amount", "50.00")
						.param("reference", ""))
				.andExpect(status().isOk())
				.andExpect(header().string("HX-Retarget", "#checkout-error"))
				.andReturn();
		assertThat(underpay.getResponse().getContentAsString()).contains("menor al total");

		// Split payment: $40.00 CARD + $100.00 CASH -> $40.00 change, $60.00 net cash
		MvcResult paidRes = mockMvc.perform(post("/pos/checkout")
						.with(csrf())
						.param("method", "CARD", "CASH")
						.param("amount", "40.00", "100.00")
						.param("reference", "TERM-42", ""))
				.andExpect(status().isOk())
				.andReturn();

		String body = paidRes.getResponse().getContentAsString();
		assertThat(body).contains("T-").contains("Cambio").contains("$40.00");

		Sale paid = sales.findById(openSale.getId()).orElseThrow();
		assertThat(paid.getStatus()).isEqualTo(SaleStatus.PAID);
		assertThat(paid.getTicketNumber()).startsWith("T-");
		assertThat(products.findById(mug.getId()).orElseThrow().getStockQty()).isEqualTo(4);
		assertThat(payments.findBySaleIdOrderByIdAsc(paid.getId())).hasSize(2);

		// Open cart is now empty and shift cash sales reflects $60.00 net cash
		assertThat(cartService.currentCart().orElseThrow().sale()).isNull();
		ShiftSummary shiftSummary = shiftService.summarize(shift);
		assertThat(shiftSummary.cashSalesCents()).isEqualTo(6_000L);
		assertThat(shiftSummary.expectedCashCents()).isEqualTo(56_000L);
	}
}
