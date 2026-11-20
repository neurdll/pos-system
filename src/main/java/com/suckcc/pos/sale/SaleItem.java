package com.suckcc.pos.sale;

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
@Table(name = "sale_items")
@Getter
@Setter
@NoArgsConstructor
public class SaleItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "sale_id", nullable = false)
	private Long saleId;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(name = "product_name_snapshot", nullable = false, length = 150)
	private String productNameSnapshot;

	@Column(name = "sku_snapshot", nullable = false, length = 50)
	private String skuSnapshot;

	@Column(nullable = false)
	private int qty;

	@Column(name = "unit_price_cents", nullable = false)
	private long unitPriceCents;

	@Column(name = "tax_rate_bps", nullable = false)
	private int taxRateBps;

	@Column(name = "line_subtotal_cents", nullable = false)
	private long lineSubtotalCents;

	@Column(name = "line_tax_cents", nullable = false)
	private long lineTaxCents;

	@Column(name = "line_total_cents", nullable = false)
	private long lineTotalCents;

	public PricingCalculator.LineAmounts toLineAmounts() {
		return new PricingCalculator.LineAmounts(lineSubtotalCents, lineTaxCents, lineTotalCents);
	}
}
