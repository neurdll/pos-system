package com.suckcc.pos.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	List<Payment> findBySaleIdOrderByIdAsc(Long saleId);

	@Query(value = """
			SELECT COALESCE(SUM(p.amount_cents), 0)
			FROM payments p
			JOIN sales s ON s.id = p.sale_id
			WHERE s.shift_id = :shiftId
			  AND s.status IN ('PAID', 'PARTIAL_RETURN', 'RETURNED')
			  AND p.method = 'CASH'
			""", nativeQuery = true)
	long sumCashSalesForShift(@Param("shiftId") Long shiftId);
}
