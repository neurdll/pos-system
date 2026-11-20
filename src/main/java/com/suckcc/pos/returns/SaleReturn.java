package com.suckcc.pos.returns;

import com.suckcc.pos.payment.PaymentMethod;
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
@Table(name = "returns")
@Getter
@Setter
@NoArgsConstructor
public class SaleReturn {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "sale_id", nullable = false)
	private Long saleId;

	@Column(name = "shift_id", nullable = false)
	private Long shiftId;

	@Column(name = "processed_by", nullable = false)
	private Long processedBy;

	@Enumerated(EnumType.STRING)
	@Column(name = "refund_method", nullable = false, length = 20)
	private PaymentMethod refundMethod;

	@Column(name = "total_refund_cents", nullable = false)
	private long totalRefundCents;

	@Column(nullable = false, length = 200)
	private String reason;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt = OffsetDateTime.now();
}
