package com.suckcc.pos.payment;

import com.suckcc.pos.catalog.ProductRepository;
import com.suckcc.pos.inventory.InventoryService;
import com.suckcc.pos.sale.Sale;
import com.suckcc.pos.sale.SaleItem;
import com.suckcc.pos.sale.SaleItemRepository;
import com.suckcc.pos.sale.SaleRepository;
import com.suckcc.pos.sale.SaleStatus;
import com.suckcc.pos.shift.CashShift;
import com.suckcc.pos.shift.ShiftService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CheckoutService {

	private final SaleRepository sales;
	private final SaleItemRepository saleItems;
	private final ProductRepository products;
	private final PaymentRepository payments;
	private final ShiftService shiftService;
	private final InventoryService inventoryService;

	public CheckoutService(
			SaleRepository sales,
			SaleItemRepository saleItems,
			ProductRepository products,
			PaymentRepository payments,
			ShiftService shiftService,
			InventoryService inventoryService
	) {
		this.sales = sales;
		this.saleItems = saleItems;
		this.products = products;
		this.payments = payments;
		this.shiftService = shiftService;
		this.inventoryService = inventoryService;
	}

	public record CheckoutResult(Sale sale, List<Payment> payments, long changeCents) {
	}

	@Transactional
	public CheckoutResult checkoutOpenSale(Long cashierId, List<PaymentCalculator.TenderInput> tenders) {
		CashShift openShift = shiftService.findOpenShift()
				.orElseThrow(() -> new IllegalStateException("No hay un turno de caja abierto"));
		Sale sale = sales.findByShiftIdAndStatus(openShift.getId(), SaleStatus.OPEN)
				.orElseThrow(() -> new IllegalStateException("No hay carrito activo"));
		List<SaleItem> items = saleItems.findBySaleIdOrderByIdAsc(sale.getId());
		if (items.isEmpty()) {
			throw new IllegalStateException("El carrito está vacío");
		}

		PaymentCalculator.Allocation allocation = sale.getTotalCents() == 0L
				? new PaymentCalculator.Allocation(List.of(), 0L, 0L)
				: PaymentCalculator.allocate(sale.getTotalCents(), tenders);

		for (SaleItem item : items) {
			int updated = products.deductStockIfAvailable(item.getProductId(), item.getQty());
			if (updated == 0) {
				throw new IllegalStateException("Stock insuficiente para " + item.getProductNameSnapshot());
			}
		}

		sale.setCashierId(cashierId);
		sale.setTicketNumber(String.format("T-%06d", sales.nextTicketSequence()));
		sale.setStatus(SaleStatus.PAID);
		sale.setCompletedAt(OffsetDateTime.now());
		sales.save(sale);

		for (SaleItem item : items) {
			inventoryService.recordSaleDeduction(
					item.getProductId(),
					item.getQty(),
					sale.getId(),
					sale.getTicketNumber(),
					cashierId
			);
		}

		List<Payment> savedPayments = new ArrayList<>(allocation.lines().size());
		for (PaymentCalculator.AllocatedTender line : allocation.lines()) {
			Payment payment = new Payment();
			payment.setSaleId(sale.getId());
			payment.setMethod(line.method());
			payment.setAmountCents(line.amountCents());
			payment.setTenderedCents(line.tenderedCents());
			payment.setChangeCents(line.changeCents());
			payment.setReference(line.reference());
			savedPayments.add(payments.save(payment));
		}

		return new CheckoutResult(sale, savedPayments, allocation.totalChangeCents());
	}
}
