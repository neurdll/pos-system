package com.suckcc.pos.returns;

import com.suckcc.pos.inventory.InventoryService;
import com.suckcc.pos.payment.PaymentMethod;
import com.suckcc.pos.sale.Sale;
import com.suckcc.pos.sale.SaleItem;
import com.suckcc.pos.sale.SaleItemRepository;
import com.suckcc.pos.sale.SaleRepository;
import com.suckcc.pos.sale.SaleStatus;
import com.suckcc.pos.shift.CashShift;
import com.suckcc.pos.shift.ShiftService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReturnService {

	private final SaleRepository sales;
	private final SaleItemRepository saleItems;
	private final SaleReturnRepository returns;
	private final ReturnItemRepository returnItems;
	private final ShiftService shiftService;
	private final InventoryService inventoryService;

	public ReturnService(
			SaleRepository sales,
			SaleItemRepository saleItems,
			SaleReturnRepository returns,
			ReturnItemRepository returnItems,
			ShiftService shiftService,
			InventoryService inventoryService
	) {
		this.sales = sales;
		this.saleItems = saleItems;
		this.returns = returns;
		this.returnItems = returnItems;
		this.shiftService = shiftService;
		this.inventoryService = inventoryService;
	}

	public record ReturnableLine(SaleItem saleItem, int alreadyReturnedQty, int remainingQty) {
	}

	public record ReturnLineRequest(Long saleItemId, int qty, boolean restock) {
	}

	public List<ReturnableLine> returnableLines(Long saleId) {
		return saleItems.findBySaleIdOrderByIdAsc(saleId).stream()
				.map(item -> {
					int returned = returnItems.sumReturnedQtyForSaleItem(item.getId());
					return new ReturnableLine(item, returned, Math.max(0, item.getQty() - returned));
				})
				.toList();
	}

	@Transactional
	public SaleReturn processReturn(
			Long userId,
			Long saleId,
			PaymentMethod refundMethod,
			String reason,
			List<ReturnLineRequest> requestedLines
	) {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("El motivo de la devolución es obligatorio");
		}
		CashShift openShift = shiftService.findOpenShift()
				.orElseThrow(() -> new IllegalStateException("No hay un turno de caja abierto"));
		Sale sale = sales.findById(saleId)
				.orElseThrow(() -> new IllegalArgumentException("Ticket no encontrado"));
		if (sale.getStatus() != SaleStatus.PAID && sale.getStatus() != SaleStatus.PARTIAL_RETURN) {
			throw new IllegalStateException("Este ticket no admite devoluciones");
		}

		List<ReturnableLine> currentLines = returnableLines(saleId);
		Map<Long, ReturnableLine> byItemId = currentLines.stream()
				.collect(Collectors.toMap(l -> l.saleItem().getId(), Function.identity()));

		List<ReturnLineRequest> nonZero = requestedLines == null ? List.of() : requestedLines.stream()
				.filter(r -> r.qty() > 0)
				.toList();
		if (nonZero.isEmpty()) {
			throw new IllegalArgumentException("Selecciona al menos una unidad para devolver");
		}

		long grossItemsTotal = currentLines.stream()
				.mapToLong(l -> l.saleItem().getLineTotalCents())
				.sum();
		long alreadyRefunded = returns.sumRefundedForSale(saleId);
		long remainingRefundCap = Math.max(0L, sale.getTotalCents() - alreadyRefunded);

		int totalRemainingBefore = currentLines.stream().mapToInt(ReturnableLine::remainingQty).sum();
		int totalRequestedNow = 0;

		List<ReturnItem> toPersist = new ArrayList<>();
		long computedRefundTotal = 0L;

		for (ReturnLineRequest req : nonZero) {
			ReturnableLine line = byItemId.get(req.saleItemId());
			if (line == null) {
				throw new IllegalArgumentException("Artículo inválido para este ticket");
			}
			if (req.qty() > line.remainingQty()) {
				throw new IllegalArgumentException("Cantidad a devolver excede lo disponible");
			}
			totalRequestedNow += req.qty();

			long grossSlice = Math.round((line.saleItem().getLineTotalCents() * (double) req.qty()) / line.saleItem().getQty());
			long netLineRefund = grossItemsTotal == 0L
					? 0L
					: Math.round((grossSlice * (double) sale.getTotalCents()) / grossItemsTotal);
			computedRefundTotal += netLineRefund;

			ReturnItem item = new ReturnItem();
			item.setSaleItemId(line.saleItem().getId());
			item.setProductId(line.saleItem().getProductId());
			item.setQty(req.qty());
			item.setRefundAmountCents(netLineRefund);
			item.setRestock(req.restock());
			toPersist.add(item);
		}

		boolean fullReturnNow = (totalRequestedNow == totalRemainingBefore);
		long finalRefund = fullReturnNow
				? remainingRefundCap
				: Math.min(computedRefundTotal, remainingRefundCap);
		if (finalRefund <= 0L) {
			throw new IllegalStateException("El ticket no tiene saldo reembolsable pendiente");
		}

		SaleReturn saleReturn = new SaleReturn();
		saleReturn.setSaleId(sale.getId());
		saleReturn.setShiftId(openShift.getId());
		saleReturn.setProcessedBy(userId);
		saleReturn.setRefundMethod(refundMethod);
		saleReturn.setTotalRefundCents(finalRefund);
		saleReturn.setReason(reason.trim());
		returns.save(saleReturn);

		for (ReturnItem item : toPersist) {
			item.setReturnId(saleReturn.getId());
			returnItems.save(item);
			if (item.isRestock()) {
				inventoryService.recordReturnRestock(
						item.getProductId(),
						item.getQty(),
						saleReturn.getId(),
						sale.getTicketNumber(),
						userId
				);
			}
		}

		sale.setStatus(fullReturnNow ? SaleStatus.RETURNED : SaleStatus.PARTIAL_RETURN);
		sales.save(sale);
		return saleReturn;
	}
}
