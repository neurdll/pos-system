package com.suckcc.pos.shared;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Pure formatting and parsing helpers for integer minor-unit money values (cents).
 */
@Component("money")
public class Money {

	private static final Locale CURRENCY_LOCALE = Locale.US;

	public String format(long cents) {
		return formatCents(cents);
	}

	public static String formatCents(long cents) {
		BigDecimal amount = BigDecimal.valueOf(cents, 2);
		NumberFormat formatter = NumberFormat.getCurrencyInstance(CURRENCY_LOCALE);
		return formatter.format(amount);
	}

	public static long parseToCents(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new IllegalArgumentException("Amount cannot be blank");
		}
		String normalized = raw.trim().replace("$", "").replace(",", "");
		BigDecimal decimal = new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
		return decimal.movePointRight(2).longValueExact();
	}
}
