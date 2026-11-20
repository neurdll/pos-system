package com.suckcc.pos.sale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

	Optional<Sale> findByShiftIdAndStatus(Long shiftId, SaleStatus status);

	Optional<Sale> findByTicketNumber(String ticketNumber);

	List<Sale> findTop30ByStatusNotOrderByCompletedAtDescIdDesc(SaleStatus status);

	@Query("""
			SELECT s FROM Sale s
			WHERE s.status <> com.suckcc.pos.sale.SaleStatus.OPEN
			  AND (:q = '' OR LOWER(COALESCE(s.ticketNumber, '')) LIKE LOWER(CONCAT('%', :q, '%')))
			ORDER BY s.completedAt DESC, s.id DESC
			""")
	List<Sale> searchCompletedTickets(@Param("q") String query);

	@Query(value = "SELECT nextval('ticket_number_seq')", nativeQuery = true)
	long nextTicketSequence();
}
