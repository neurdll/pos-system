package com.suckcc.pos.sale;

import com.suckcc.pos.catalog.Product;
import com.suckcc.pos.catalog.ProductRepository;
import com.suckcc.pos.shift.CashShift;
import com.suckcc.pos.shift.ShiftService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CartService {

	private final SaleRepository sales;
	private final SaleItemRepository items;
	private final ProductRepository products;
	private final ShiftService shiftService;

	public CartService(
			SaleRepository sales,
			SaleItemRepository items,
			ProductRepository products,
			ShiftService shiftService
	) {
		this.sales = sales;
		this.items = items;
		this.products = products;
		this.shiftService = shiftService;
	}

	public record CartView(CashShift shift, Sale sale, List<SaleItem> items) {
	}

	public Optional<CartView> currentCart() {
		return shiftService.findOpenShift().map(shift -> {
			Sale sale = sales.findByShiftIdAndStatus(shift.getId(), SaleStatus.OPEN).orElse(null);
			List<SaleItem> cartItems = sale == null ? List.of() : items.findBySaleIdOrderByIdAsc(sale.getId());
			return new CartView(shift, sale, cartItems);
		});
	}

	@Transactional
	public CartView getOrCreateCart(Long cashierId) {
		CashShift shift = requireOpenShift();
		Sale sale = getOrCreateOpenSale(shift.getId(), cashierId);
		return new CartView(shift, sale, items.findBySaleIdOrderByIdAsc(sale.getId()));
	}

	@Transactional
	public CartView addByCode(Long cashierId, String rawCode) {
		requireOpenShift();
		if (rawCode == null || rawCode.isBlank()) {
			throw new IllegalArgumentException("Ingresa un código de barras o SKU");
		}
		Product product = products.findActiveByCode(rawCode.trim())
				.orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + rawCode.trim()));
		return addProductInternal(cashierId, product);
	}

	@Transactional
	public CartView addProduct(Long cashierId, Long productId) {
		requireOpenShift();
		Product product = products.findById(productId)
				.filter(Product::isActive)
				.orElseThrow(() -> new IllegalArgumentException("Producto no disponible"));
		return addProductInternal(cashierId, product);
	}

	@Transactional
	public CartView adjustItemQty(Long cashierId, Long itemId, Integer qty, Integer delta) {
		CashShift shift = requireOpenShift();
		Sale sale = sales.findByShiftIdAndStatus(shift.getId(), SaleStatus.OPEN)
				.orElseThrow(() -> new IllegalStateException("No hay carrito activo"));
		SaleItem item = items.findById(itemId)
				.filter(i -> i.getSaleId().equals(sale.getId()))
				.orElseThrow(() -> new IllegalArgumentException("Artículo no encontrado en el carrito"));

		int targetQty = qty != null ? qty : item.getQty() + (delta != null ? delta : 0);
		if (targetQty <= 0) {
			items.delete(item);
		} else {
			Product product = products.findById(item.getProductId())
					.orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
			ensureStock(product, targetQty);
			applyLinePricing(item, product, targetQty);
			items.save(item);
		}
		sale.setCashierId(cashierId);
		return recalculateAndReturn(shift, sale);
	}

	@Transactional
	public CartView applyDiscount(Long cashierId, long discountCents) {
		if (discountCents < 0) {
			throw new IllegalArgumentException("El descuento no puede ser negativo");
		}
		CashShift shift = requireOpenShift();
		Sale sale = getOrCreateOpenSale(shift.getId(), cashierId);
		sale.setCashierId(cashierId);
		sale.setDiscountCents(discountCents);
		return recalculateAndReturn(shift, sale);
	}

	@Transactional
	public CartView clearCart(Long cashierId) {
		CashShift shift = requireOpenShift();
		Sale sale = sales.findByShiftIdAndStatus(shift.getId(), SaleStatus.OPEN).orElse(null);
		if (sale == null) {
			return new CartView(shift, null, List.of());
		}
		items.deleteBySaleId(sale.getId());
		sale.setCashierId(cashierId);
		sale.setDiscountCents(0L);
		return recalculateAndReturn(shift, sale);
	}

	private CartView addProductInternal(Long cashierId, Product product) {
		CashShift shift = requireOpenShift();
		Sale sale = getOrCreateOpenSale(shift.getId(), cashierId);
		sale.setCashierId(cashierId);

		SaleItem item = items.findBySaleIdAndProductId(sale.getId(), product.getId())
				.orElseGet(() -> {
					SaleItem created = new SaleItem();
					created.setSaleId(sale.getId());
					created.setProductId(product.getId());
					created.setQty(0);
					return created;
				});

		int targetQty = item.getQty() + 1;
		ensureStock(product, targetQty);
		applyLinePricing(item, product, targetQty);
		items.save(item);
		return recalculateAndReturn(shift, sale);
	}

	private Sale getOrCreateOpenSale(Long shiftId, Long cashierId) {
		return sales.findByShiftIdAndStatus(shiftId, SaleStatus.OPEN)
				.orElseGet(() -> {
					Sale created = new Sale();
					created.setShiftId(shiftId);
					created.setCashierId(cashierId);
					created.setStatus(SaleStatus.OPEN);
					return sales.save(created);
				});
	}

	private CartView recalculateAndReturn(CashShift shift, Sale sale) {
		List<SaleItem> cartItems = items.findBySaleIdOrderByIdAsc(sale.getId());
		PricingCalculator.CartTotals totals = PricingCalculator.calculateCart(
				cartItems.stream().map(SaleItem::toLineAmounts).toList(),
				sale.getDiscountCents()
		);
		sale.setSubtotalCents(totals.subtotalCents());
		sale.setTaxCents(totals.taxCents());
		sale.setDiscountCents(totals.discountCents());
		sale.setTotalCents(totals.totalCents());
		sales.save(sale);
		return new CartView(shift, sale, cartItems);
	}

	private CashShift requireOpenShift() {
		return shiftService.findOpenShift()
				.orElseThrow(() -> new IllegalStateException("No hay un turno de caja abierto"));
	}

	private static void ensureStock(Product product, int targetQty) {
		if (product.isTrackInventory() && product.getStockQty() < targetQty) {
			throw new IllegalArgumentException(
					"Stock insuficiente para " + product.getName() + " (disponible: " + product.getStockQty() + ")"
			);
		}
	}

	private static void applyLinePricing(SaleItem item, Product product, int qty) {
		PricingCalculator.LineAmounts amounts =
				PricingCalculator.priceLine(product.getPriceCents(), product.getTaxRateBps(), qty);
		item.setProductNameSnapshot(product.getName());
		item.setSkuSnapshot(product.getSku());
		item.setQty(qty);
		item.setUnitPriceCents(product.getPriceCents());
		item.setTaxRateBps(product.getTaxRateBps());
		item.setLineSubtotalCents(amounts.subtotalCents());
		item.setLineTaxCents(amounts.taxCents());
		item.setLineTotalCents(amounts.totalCents());
	}
}
