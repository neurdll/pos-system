package com.suckcc.pos.inventory;

import com.suckcc.pos.catalog.Product;
import com.suckcc.pos.catalog.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryService {

	private final StockMovementRepository movements;
	private final ProductRepository products;

	public InventoryService(StockMovementRepository movements, ProductRepository products) {
		this.movements = movements;
		this.products = products;
	}

	public List<StockMovementRepository.LedgerRow> ledger(Long productId, StockMovementType type) {
		return movements.findLedger(productId, type);
	}

	@Transactional
	public void recordSaleDeduction(Long productId, int qty, Long saleId, String ticketNumber, Long userId) {
		Product product = products.findById(productId).orElse(null);
		if (product == null || !product.isTrackInventory() || qty <= 0) {
			return;
		}
		StockMovement movement = new StockMovement();
		movement.setProductId(productId);
		movement.setType(StockMovementType.SALE);
		movement.setQtyDelta(-qty);
		movement.setReferenceId(saleId);
		movement.setNote("Venta " + ticketNumber);
		movement.setCreatedBy(userId);
		movements.save(movement);
	}

	@Transactional
	public void recordReturnRestock(Long productId, int qty, Long returnId, String ticketNumber, Long userId) {
		if (qty <= 0) {
			return;
		}
		Product product = products.findById(productId)
				.orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
		if (!product.isTrackInventory()) {
			return;
		}
		products.adjustStockIfNonNegative(productId, qty);
		StockMovement movement = new StockMovement();
		movement.setProductId(productId);
		movement.setType(StockMovementType.RETURN);
		movement.setQtyDelta(qty);
		movement.setReferenceId(returnId);
		movement.setNote("Devolución " + ticketNumber);
		movement.setCreatedBy(userId);
		movements.save(movement);
	}

	@Transactional
	public StockMovement adjustStock(
			Long userId,
			Long productId,
			StockMovementType type,
			int qtyDelta,
			String note
	) {
		if (type != StockMovementType.ADJUSTMENT && type != StockMovementType.PURCHASE) {
			throw new IllegalArgumentException("Tipo de ajuste manual inválido");
		}
		if (qtyDelta == 0) {
			throw new IllegalArgumentException("La cantidad del movimiento no puede ser cero");
		}
		if (type == StockMovementType.PURCHASE && qtyDelta < 0) {
			throw new IllegalArgumentException("Una compra debe sumar stock positivo");
		}
		Product product = products.findById(productId)
				.orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
		if (!product.isTrackInventory()) {
			throw new IllegalArgumentException("El producto no tiene control de inventario");
		}
		int updated = products.adjustStockIfNonNegative(productId, qtyDelta);
		if (updated == 0) {
			throw new IllegalArgumentException("El stock resultante no puede ser negativo");
		}

		StockMovement movement = new StockMovement();
		movement.setProductId(productId);
		movement.setType(type);
		movement.setQtyDelta(qtyDelta);
		movement.setNote((note == null || note.isBlank()) ? type.name() : note.trim());
		movement.setCreatedBy(userId);
		return movements.save(movement);
	}
}
