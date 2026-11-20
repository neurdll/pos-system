package com.suckcc.pos.shift;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "cash_shifts")
@Getter
@Setter
@NoArgsConstructor
public class CashShift {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "opened_by", nullable = false)
	private Long openedBy;

	@Column(name = "opened_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime openedAt = OffsetDateTime.now();

	@Column(name = "opening_float_cents", nullable = false)
	private long openingFloatCents;

	@Column(name = "closed_by")
	private Long closedBy;

	@Column(name = "closed_at")
	private OffsetDateTime closedAt;

	@Column(name = "counted_cash_cents")
	private Long countedCashCents;

	@Column(name = "expected_cash_cents")
	private Long expectedCashCents;

	@Column(name = "variance_cents")
	private Long varianceCents;

	@Column(length = 255)
	private String notes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ShiftStatus status = ShiftStatus.OPEN;
}
