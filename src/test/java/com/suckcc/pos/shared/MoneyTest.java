package com.suckcc.pos.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

	@Test
	void formatsPositiveZeroAndNegativeCents() {
		assertThat(Money.formatCents(0)).isEqualTo("$0.00");
		assertThat(Money.formatCents(1250)).isEqualTo("$12.50");
		assertThat(Money.formatCents(100005)).isEqualTo("$1,000.05");
		assertThat(Money.formatCents(-499)).isEqualTo("-$4.99");
	}

	@Test
	void parsesDecimalStringsToCents() {
		assertThat(Money.parseToCents("19.99")).isEqualTo(1999L);
		assertThat(Money.parseToCents("$1,250.5")).isEqualTo(125050L);
		assertThat(Money.parseToCents("0")).isEqualTo(0L);
		assertThatThrownBy(() -> Money.parseToCents(" "))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
