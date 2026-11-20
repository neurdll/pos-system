package com.suckcc.pos.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 50)
	private String sku;

	@Column(unique = true, length = 64)
	private String barcode;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(length = 80)
	private String category;

	@Column(name = "price_cents", nullable = false)
	private long priceCents;

	@Column(name = "tax_rate_bps", nullable = false)
	private int taxRateBps = 1600;

	@Column(name = "stock_qty", nullable = false)
	private int stockQty;

	@Column(name = "track_inventory", nullable = false)
	private boolean trackInventory = true;

	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false)
	private OffsetDateTime updatedAt = OffsetDateTime.now();
}
