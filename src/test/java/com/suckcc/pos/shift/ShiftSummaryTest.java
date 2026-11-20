package com.suckcc.pos.shift;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShiftSummaryTest {

	@Test
	void calculatesExpectedCashAndVarianceFromMovementsAndCashFlow() {
		CashMovement in = new CashMovement();
		in.setType(CashMovementType.IN);
		in.setAmountCents(20_000L);

		CashMovement out = new CashMovement();
		out.setType(CashMovementType.OUT);
		out.setAmountCents(5_000L);

		ShiftSummary summary = ShiftSummary.calculate(
				50_000L, // $500.00 opening float
				35_000L, // $350.00 cash sales
				2_500L,  // $25.00 cash refunds
				List.of(in, out),
				97_000L  // $970.00 counted -> -$5.00 variance
		);

		assertThat(summary.cashInCents()).isEqualTo(20_000L);
		assertThat(summary.cashOutCents()).isEqualTo(5_000L);
		assertThat(summary.expectedCashCents()).isEqualTo(97_500L);
		assertThat(summary.varianceCents()).isEqualTo(-500L);
	}
}
