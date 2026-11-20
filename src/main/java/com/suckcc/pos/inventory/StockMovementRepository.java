package com.suckcc.pos.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

	record LedgerRow(
			Long id,
			Long productId,
			String sku,
			String productName,
			StockMovementType type,
			int qtyDelta,
			Long referenceId,
			String note,
			String actorName,
			OffsetDateTime createdAt
	) {
	}

	@Query("""
			SELECT new com.suckcc.pos.inventory.StockMovementRepository$LedgerRow(
				m.id, m.productId, p.sku, p.name, m.type, m.qtyDelta, m.referenceId, m.note, u.displayName, m.createdAt
			)
			FROM StockMovement m
			JOIN Product p ON p.id = m.productId
			JOIN PosUser u ON u.id = m.createdBy
			WHERE (:productId IS NULL OR m.productId = :productId)
			  AND (:type IS NULL OR m.type = :type)
			ORDER BY m.createdAt DESC, m.id DESC
			""")
	List<LedgerRow> findLedger(@Param("productId") Long productId, @Param("type") StockMovementType type);
}
