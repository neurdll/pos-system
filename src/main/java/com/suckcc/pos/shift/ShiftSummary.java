package com.suckcc.pos.shift;

import java.util.List;

/**
 * Pure calculation snapshot for cash drawer balance and closeout variance.
 */
public record ShiftSummary(
		long openingFloatCents,
		long cashSalesCents,
		long cashRefundsCents,
		long cashInCents,
		long cashOutCents,
		long expectedCashCents,
		Long countedCashCents,
		Long varianceCents
) {

	public static ShiftSummary calculate(
			long openingFloatCents,
			long cashSalesCents,
			long cashRefundsCents,
			List<CashMovement> movements,
			Long countedCashCents
	) {
		long cashIn = movements.stream()
				.filter(m -> m.getType() == CashMovementType.IN)
				.mapToLong(CashMovement::getAmountCents)
				.sum();
		long cashOut = movements.stream()
				.filter(m -> m.getType() == CashMovementType.OUT)
				.mapToLong(CashMovement::getAmountCents)
				.sum();
		long expected = openingFloatCents + cashSalesCents - cashRefundsCents + cashIn - cashOut;
		Long variance = countedCashCents == null ? null : countedCashCents - expected;
		return new ShiftSummary(
				openingFloatCents,
				cashSalesCents,
				cashRefundsCents,
				cashIn,
				cashOut,
				expected,
				countedCashCents,
				variance
		);
	}
}
