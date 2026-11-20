package com.suckcc.pos.returns;

import com.suckcc.pos.auth.PosUserRepository;
import com.suckcc.pos.catalog.Product;
import com.suckcc.pos.catalog.ProductRepository;
import com.suckcc.pos.inventory.InventoryService;
import com.suckcc.pos.inventory.StockMovementType;
import com.suckcc.pos.payment.CheckoutService;
import com.suckcc.pos.payment.PaymentCalculator;
import com.suckcc.pos.payment.PaymentMethod;
import com.suckcc.pos.sale.CartService;
import com.suckcc.pos.sale.Sale;
import com.suckcc.pos.sale.SaleItem;
import com.suckcc.pos.sale.SaleItemRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
class ReturnControllerTest {

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
	private SaleRepository sales;

	@Autowired
	private SaleItemRepository saleItems;

	@Autowired
	private ReturnService returnService;

	@Autowired
	private InventoryService inventoryService;

	@Test
	@WithUserDetails("cajero1")
	void partialAndFullReturnRestockInventoryAndReduceShiftExpectedCash() throws Exception {
		Long cashierId = users.findByUsernameAndActiveTrue("cajero1").orElseThrow().getId();
		CashShift shift = shiftService.openShift(cashierId, 50_000L);

		Product shirt = new Product();
		shirt.setSku("PLAYERA-01");
		shirt.setName("Playera Negra");
		shirt.setPriceCents(10_000L);
		shirt.setTaxRateBps(1600);
		shirt.setStockQty(5);
		shirt.setTrackInventory(true);
		shirt.setActive(true);
		products.save(shirt);

		cartService.addProduct(cashierId, shirt.getId());
		cartService.addProduct(cashierId, shirt.getId());
		Sale paidSale = checkoutService.checkoutOpenSale(
				cashierId,
				List.of(new PaymentCalculator.TenderInput(PaymentMethod.CASH, 20_000L, null))
		).sale();
		SaleItem item = saleItems.findBySaleIdOrderByIdAsc(paidSale.getId()).getFirst();

		assertThat(products.findById(shirt.getId()).orElseThrow().getStockQty()).isEqualTo(3);

		mockMvc.perform(get("/returns/new").param("saleId", paidSale.getId().toString()))
				.andExpect(status().isOk());

		// Partial return of 1 unit in CASH with restock=true
		mockMvc.perform(post("/returns")
						.with(csrf())
						.param("saleId", paidSale.getId().toString())
						.param("refundMethod", "CASH")
						.param("reason", "Talla incorrecta")
						.param("saleItemId", item.getId().toString())
						.param("qty", "1")
						.param("restockId", item.getId().toString()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/tickets"));

		assertThat(sales.findById(paidSale.getId()).orElseThrow().getStatus()).isEqualTo(SaleStatus.PARTIAL_RETURN);
		assertThat(products.findById(shirt.getId()).orElseThrow().getStockQty()).isEqualTo(4);
		assertThat(inventoryService.ledger(shirt.getId(), StockMovementType.RETURN)).hasSize(1);

		ShiftSummary midSummary = shiftService.summarize(shift);
		assertThat(midSummary.cashSalesCents()).isEqualTo(20_000L);
		assertThat(midSummary.cashRefundsCents()).isEqualTo(10_000L);
		assertThat(midSummary.expectedCashCents()).isEqualTo(60_000L);

		// Return remaining 1 unit -> sale transitions to RETURNED
		returnService.processReturn(
				cashierId,
				paidSale.getId(),
				PaymentMethod.CASH,
				"Segunda unidad",
				List.of(new ReturnService.ReturnLineRequest(item.getId(), 1, true))
		);
		assertThat(sales.findById(paidSale.getId()).orElseThrow().getStatus()).isEqualTo(SaleStatus.RETURNED);
		assertThat(products.findById(shirt.getId()).orElseThrow().getStockQty()).isEqualTo(5);

		// Further return on fully returned ticket is rejected
		assertThatThrownBy(() -> returnService.processReturn(
				cashierId,
				paidSale.getId(),
				PaymentMethod.CASH,
				"Extra",
				List.of(new ReturnService.ReturnLineRequest(item.getId(), 1, true))
		)).isInstanceOf(IllegalStateException.class);
	}
}
