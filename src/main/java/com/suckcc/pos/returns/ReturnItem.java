package com.suckcc.pos.returns;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "return_items")
@Getter
@Setter
@NoArgsConstructor
public class ReturnItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "return_id", nullable = false)
	private Long returnId;

	@Column(name = "sale_item_id", nullable = false)
	private Long saleItemId;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(nullable = false)
	private int qty;

	@Column(name = "refund_amount_cents", nullable = false)
	private long refundAmountCents;

	@Column(nullable = false)
	private boolean restock = true;
}
