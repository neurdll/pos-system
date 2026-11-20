package com.suckcc.pos.returns;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReturnItemRepository extends JpaRepository<ReturnItem, Long> {

	@Query(value = "SELECT COALESCE(SUM(ri.qty), 0) FROM return_items ri WHERE ri.sale_item_id = :saleItemId", nativeQuery = true)
	int sumReturnedQtyForSaleItem(@Param("saleItemId") Long saleItemId);
}
