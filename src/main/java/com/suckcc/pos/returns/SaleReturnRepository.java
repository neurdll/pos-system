package com.suckcc.pos.returns;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleReturnRepository extends JpaRepository<SaleReturn, Long> {

	@Query(value = "SELECT COALESCE(SUM(r.total_refund_cents), 0) FROM returns r WHERE r.shift_id = :shiftId AND r.refund_method = 'CASH'", nativeQuery = true)
	long sumCashRefundsForShift(@Param("shiftId") Long shiftId);

	@Query(value = "SELECT COALESCE(SUM(r.total_refund_cents), 0) FROM returns r WHERE r.sale_id = :saleId", nativeQuery = true)
	long sumRefundedForSale(@Param("saleId") Long saleId);
}
