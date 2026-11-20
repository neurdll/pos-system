package com.suckcc.pos.sale;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PricingCalculatorTest {

	@Test
	void breaksDownTaxInclusiveLineAmounts() {
		PricingCalculator.LineAmounts line = PricingCalculator.priceLine(11_600L, 1600, 2);

		assertThat(line.totalCents()).isEqualTo(23_200L);
		assertThat(line.subtotalCents()).isEqualTo(20_000L);
		assertThat(line.taxCents()).isEqualTo(3_200L);
	}

	@Test
	void scalesCartSubtotalAndTaxProportionallyOnDiscount() {
		PricingCalculator.LineAmounts l1 = PricingCalculator.priceLine(11_600L, 1600, 1);
		PricingCalculator.LineAmounts l2 = PricingCalculator.priceLine(5_800L, 1600, 1);

		PricingCalculator.CartTotals totals = PricingCalculator.calculateCart(List.of(l1, l2), 1_400L);

		assertThat(totals.discountCents()).isEqualTo(1_400L);
		assertThat(totals.totalCents()).isEqualTo(16_000L);
		assertThat(totals.subtotalCents() + totals.taxCents()).isEqualTo(totals.totalCents());
	}

	@Test
	void clampsDiscountAndHandlesEmptyCart() {
		PricingCalculator.CartTotals empty = PricingCalculator.calculateCart(List.of(), 500L);
		assertThat(empty).isEqualTo(new PricingCalculator.CartTotals(0L, 0L, 0L, 0L));

		PricingCalculator.LineAmounts line = PricingCalculator.priceLine(1_000L, 0, 1);
		PricingCalculator.CartTotals capped = PricingCalculator.calculateCart(List.of(line), 9_999L);
		assertThat(capped.discountCents()).isEqualTo(1_000L);
		assertThat(capped.totalCents()).isZero();
	}
}
