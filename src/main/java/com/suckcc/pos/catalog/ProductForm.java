package com.suckcc.pos.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record ProductForm(
		@NotBlank(message = "El SKU es obligatorio")
		@Size(max = 50)
		String sku,

		@Size(max = 64)
		String barcode,

		@NotBlank(message = "El nombre es obligatorio")
		@Size(max = 150)
		String name,

		@Size(max = 80)
		String category,

		@NotNull(message = "El precio es obligatorio")
		@DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
		BigDecimal price,

		@NotNull(message = "El IVA (%) es obligatorio")
		@Min(0)
		@Max(100)
		Integer taxPercent,

		@NotNull(message = "El stock inicial es obligatorio")
		Integer stockQty,

		boolean trackInventory,
		boolean active
) {

	public static ProductForm empty() {
		return new ProductForm("", "", "", "", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), 16, 0, true, true);
	}

	public static ProductForm from(Product product) {
		return new ProductForm(
				product.getSku(),
				product.getBarcode() == null ? "" : product.getBarcode(),
				product.getName(),
				product.getCategory() == null ? "" : product.getCategory(),
				BigDecimal.valueOf(product.getPriceCents(), 2),
				product.getTaxRateBps() / 100,
				product.getStockQty(),
				product.isTrackInventory(),
				product.isActive()
		);
	}

	public void applyTo(Product product) {
		product.setSku(sku.trim());
		product.setBarcode(blankToNull(barcode));
		product.setName(name.trim());
		product.setCategory(blankToNull(category));
		product.setPriceCents(price.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact());
		product.setTaxRateBps(taxPercent * 100);
		product.setStockQty(stockQty);
		product.setTrackInventory(trackInventory);
		product.setActive(active);
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.trim();
	}
}
