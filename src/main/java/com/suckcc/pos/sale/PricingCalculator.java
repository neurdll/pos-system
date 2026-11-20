package com.suckcc.pos.sale;

import java.util.List;

/**
 * Pure tax-inclusive retail pricing calculations in integer minor units (cents).
 */
public final class PricingCalculator {

	private PricingCalculator() {
	}

	public record LineAmounts(long subtotalCents, long taxCents, long totalCents) {
	}

	public record CartTotals(long subtotalCents, long taxCents, long discountCents, long totalCents) {
	}

	public static LineAmounts priceLine(long unitPriceCents, int taxRateBps, int qty) {
		if (unitPriceCents < 0 || taxRateBps < 0 || qty <= 0) {
			throw new IllegalArgumentException("Invalid price, tax rate, or quantity");
		}
		long lineTotal = Math.multiplyExact(unitPriceCents, (long) qty);
		long lineSubtotal = Math.round((lineTotal * 10_000.0d) / (10_000 + taxRateBps));
		long lineTax = lineTotal - lineSubtotal;
		return new LineAmounts(lineSubtotal, lineTax, lineTotal);
	}

	public static CartTotals calculateCart(List<LineAmounts> lines, long requestedDiscountCents) {
		long grossSubtotal = 0L;
		long grossTax = 0L;
		long grossTotal = 0L;
		for (LineAmounts line : lines) {
			grossSubtotal += line.subtotalCents();
			grossTax += line.taxCents();
			grossTotal += line.totalCents();
		}
		if (grossTotal == 0L) {
			return new CartTotals(0L, 0L, 0L, 0L);
		}
		long discount = Math.max(0L, Math.min(requestedDiscountCents, grossTotal));
		if (discount == 0L) {
			return new CartTotals(grossSubtotal, grossTax, 0L, grossTotal);
		}
		long netTotal = grossTotal - discount;
		long netSubtotal = Math.round((grossSubtotal * (double) netTotal) / grossTotal);
		long netTax = netTotal - netSubtotal;
		return new CartTotals(netSubtotal, netTax, discount, netTotal);
	}
}
