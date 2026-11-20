package com.suckcc.pos.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

	boolean existsBySkuIgnoreCase(String sku);

	boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);

	boolean existsByBarcodeAndIdNot(String barcode, Long id);

	boolean existsByBarcode(String barcode);

	@Query("""
			SELECT p FROM Product p
			WHERE (:q = ''
			   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
			   OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :q, '%'))
			   OR LOWER(COALESCE(p.barcode, '')) LIKE LOWER(CONCAT('%', :q, '%')))
			ORDER BY p.active DESC, p.name ASC
			""")
	List<Product> search(@Param("q") String query);

	@Query("""
			SELECT p FROM Product p
			WHERE p.active = true
			  AND (:q = ''
			   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
			   OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :q, '%'))
			   OR LOWER(COALESCE(p.barcode, '')) LIKE LOWER(CONCAT('%', :q, '%')))
			ORDER BY p.name ASC
			""")
	List<Product> searchActive(@Param("q") String query);

	@Query("""
			SELECT p FROM Product p
			WHERE p.active = true
			  AND (p.barcode = :code OR LOWER(p.sku) = LOWER(:code))
			""")
	Optional<Product> findActiveByCode(@Param("code") String code);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE Product p
			SET p.stockQty = CASE WHEN p.trackInventory = true THEN p.stockQty - :qty ELSE p.stockQty END,
			    p.updatedAt = CURRENT_TIMESTAMP
			WHERE p.id = :productId AND (p.trackInventory = false OR p.stockQty >= :qty)
			""")
	int deductStockIfAvailable(@Param("productId") Long productId, @Param("qty") int qty);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE Product p
			SET p.stockQty = p.stockQty + :delta,
			    p.updatedAt = CURRENT_TIMESTAMP
			WHERE p.id = :productId AND p.stockQty + :delta >= 0
			""")
	int adjustStockIfNonNegative(@Param("productId") Long productId, @Param("delta") int delta);
}
