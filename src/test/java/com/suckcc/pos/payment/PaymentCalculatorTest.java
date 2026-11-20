package com.suckcc.pos.payment;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentCalculatorTest {

	@Test
	void allocatesMixedCardAndCashWithChange() {
		PaymentCalculator.Allocation allocation = PaymentCalculator.allocate(
				10_000L, // $100.00 sale
				List.of(
						new PaymentCalculator.TenderInput(PaymentMethod.CARD, 4_000L, "AUTH-99"),
						new PaymentCalculator.TenderInput(PaymentMethod.CASH, 10_000L, null)
				)
		);

		assertThat(allocation.totalTenderedCents()).isEqualTo(14_000L);
		assertThat(allocation.totalChangeCents()).isEqualTo(4_000L);
		assertThat(allocation.lines()).hasSize(2);
		assertThat(allocation.lines().get(0).amountCents()).isEqualTo(4_000L);
		assertThat(allocation.lines().get(0).changeCents()).isZero();
		assertThat(allocation.lines().get(1).amountCents()).isEqualTo(6_000L);
		assertThat(allocation.lines().get(1).tenderedCents()).isEqualTo(10_000L);
		assertThat(allocation.lines().get(1).changeCents()).isEqualTo(4_000L);
	}

	@Test
	void rejectsUnderpaymentAndNonCashOverpayment() {
		assertThatThrownBy(() -> PaymentCalculator.allocate(
				10_000L,
				List.of(new PaymentCalculator.TenderInput(PaymentMethod.CASH, 9_000L, null))
		)).isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> PaymentCalculator.allocate(
				10_000L,
				List.of(new PaymentCalculator.TenderInput(PaymentMethod.CARD, 12_000L, "AUTH"))
		)).isInstanceOf(IllegalArgumentException.class);
	}
}
