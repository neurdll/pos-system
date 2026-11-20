package com.suckcc.pos;

import com.suckcc.pos.catalog.Product;
import com.suckcc.pos.catalog.ProductRepository;
import com.suckcc.pos.inventory.InventoryService;
import com.suckcc.pos.sale.Sale;
import com.suckcc.pos.sale.SaleItem;
import com.suckcc.pos.sale.SaleItemRepository;
import com.suckcc.pos.sale.SaleRepository;
import com.suckcc.pos.sale.SaleStatus;
import com.suckcc.pos.shift.CashShift;
import com.suckcc.pos.shift.ShiftService;
import com.suckcc.pos.shift.ShiftStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PosEndToEndFlowTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProductRepository products;

	@Autowired
	private SaleRepository sales;

	@Autowired
	private SaleItemRepository saleItems;

	@Autowired
	private ShiftService shiftService;

	@Autowired
	private InventoryService inventoryService;

	@Test
	void fullCounterToCorteLifecycleWithPinSwitchSplitPayReturnAndReport() throws Exception {
		// 1. Login as ADMIN and create product
		MvcResult login = mockMvc.perform(post("/login")
						.with(csrf())
						.param("username", "admin")
						.param("password", "admin123"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/pos"))
				.andReturn();
		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
		assertThat(session).isNotNull();

		mockMvc.perform(post("/catalog")
						.session(session)
						.with(csrf())
						.param("sku", "E2E-001")
						.param("barcode", "750888000111")
						.param("name", "Termo Acero")
						.param("category", "Accesorios")
						.param("price", "100.00")
						.param("taxPercent", "16")
						.param("stockQty", "5")
						.param("trackInventory", "true")
						.param("active", "true"))
				.andExpect(status().is3xxRedirection());

		Product product = products.findActiveByCode("E2E-001").orElseThrow();

		// 2. PIN switch to cashier (0000) without full logout
		mockMvc.perform(post("/auth/switch-pin")
						.session(session)
						.with(csrf())
						.header("HX-Request", "true")
						.param("pin", "0000"))
				.andExpect(status().isOk());

		// 3. Open shift with $500.00 float + $50.00 cash IN
		mockMvc.perform(post("/shifts/open")
						.session(session)
						.with(csrf())
						.param("openingFloat", "500.00"))
				.andExpect(status().is3xxRedirection());
		CashShift shift = shiftService.findOpenShift().orElseThrow();

		mockMvc.perform(post("/shifts/movements")
						.session(session)
						.with(csrf())
						.header("HX-Request", "true")
						.param("type", "IN")
						.param("amount", "50.00")
						.param("reason", "Fondo extra"))
				.andExpect(status().isOk());

		// 4. Scan product twice ($200.00) and apply $20.00 discount ($180.00)
		mockMvc.perform(post("/pos/cart/scan")
						.session(session)
						.with(csrf())
						.param("code", "750888000111"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/pos/cart/scan")
						.session(session)
						.with(csrf())
						.param("code", "E2E-001"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/pos/cart/discount")
						.session(session)
						.with(csrf())
						.param("discount", "20.00"))
				.andExpect(status().isOk());

		// 5. Split checkout: $80.00 CARD + $150.00 CASH -> $50.00 change, $100.00 net cash
		mockMvc.perform(post("/pos/checkout")
						.session(session)
						.with(csrf())
						.param("method", "CARD", "CASH")
						.param("amount", "80.00", "150.00")
						.param("reference", "AUTH-E2E", ""))
				.andExpect(status().isOk());

		Sale paidSale = sales.searchCompletedTickets("").getFirst();
		assertThat(paidSale.getStatus()).isEqualTo(SaleStatus.PAID);
		assertThat(paidSale.getTotalCents()).isEqualTo(18_000L);
		assertThat(products.findById(product.getId()).orElseThrow().getStockQty()).isEqualTo(3);

		// 6. Print-ready ticket view
		MvcResult printRes = mockMvc.perform(get("/tickets/{id}/print", paidSale.getId()).session(session))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(printRes.getResponse().getContentAsString())
				.contains(paidSale.getTicketNumber())
				.contains("Cajero Turno 1")
				.contains("$50.00");

		// 7. Partial return of 1 unit ($90.00 cash refund, restock -> stock=4)
		SaleItem item = saleItems.findBySaleIdOrderByIdAsc(paidSale.getId()).getFirst();
		mockMvc.perform(post("/returns")
						.session(session)
						.with(csrf())
						.param("saleId", paidSale.getId().toString())
						.param("refundMethod", "CASH")
						.param("reason", "Cambio parcial")
						.param("saleItemId", item.getId().toString())
						.param("qty", "1")
						.param("restockId", item.getId().toString()))
				.andExpect(status().is3xxRedirection());

		assertThat(products.findById(product.getId()).orElseThrow().getStockQty()).isEqualTo(4);
		assertThat(inventoryService.ledger(product.getId(), null)).hasSize(2);

		// 8. Close shift: expected = $500 + $50 + $100 - $90 = $560.00; count $558.00 -> -$2.00 variance
		mockMvc.perform(post("/shifts/close")
						.session(session)
						.with(csrf())
						.param("countedCash", "558.00")
						.param("notes", "Cierre E2E"))
				.andExpect(status().is3xxRedirection());

		CashShift closed = shiftService.recentShifts().stream()
				.filter(s -> s.getId().equals(shift.getId()))
				.findFirst()
				.orElseThrow();
		assertThat(closed.getStatus()).isEqualTo(ShiftStatus.CLOSED);
		assertThat(closed.getExpectedCashCents()).isEqualTo(56_000L);
		assertThat(closed.getVarianceCents()).isEqualTo(-200L);

		// 9. PIN switch back to ADMIN (1234) and verify daily + Corte Z report
		mockMvc.perform(post("/auth/switch-pin")
						.session(session)
						.with(csrf())
						.header("HX-Request", "true")
						.param("pin", "1234"))
				.andExpect(status().isOk());

		MvcResult reportRes = mockMvc.perform(get("/reports")
						.session(session)
						.param("shiftId", shift.getId().toString()))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(reportRes.getResponse().getContentAsString())
				.contains("$180.00")
				.contains("$90.00")
				.contains("Corte Z (Cerrado)")
				.contains("-$2.00")
				.contains("Termo Acero");
	}
}
