package com.suckcc.pos.shift;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {

	List<CashMovement> findByShiftIdOrderByCreatedAtDesc(Long shiftId);
}
