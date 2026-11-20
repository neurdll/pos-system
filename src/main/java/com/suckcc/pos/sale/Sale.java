package com.suckcc.pos.sale;

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
@Table(name = "sales")
@Getter
@Setter
@NoArgsConstructor
public class Sale {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "ticket_number", unique = true, length = 32)
	private String ticketNumber;

	@Column(name = "shift_id", nullable = false)
	private Long shiftId;

	@Column(name = "cashier_id", nullable = false)
	private Long cashierId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private SaleStatus status = SaleStatus.OPEN;

	@Column(name = "subtotal_cents", nullable = false)
	private long subtotalCents;

	@Column(name = "tax_cents", nullable = false)
	private long taxCents;

	@Column(name = "discount_cents", nullable = false)
	private long discountCents;

	@Column(name = "total_cents", nullable = false)
	private long totalCents;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt = OffsetDateTime.now();

	@Column(name = "completed_at")
	private OffsetDateTime completedAt;
}
