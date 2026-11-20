package com.suckcc.pos.shift;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CashShiftRepository extends JpaRepository<CashShift, Long> {

	Optional<CashShift> findFirstByStatusOrderByOpenedAtDesc(ShiftStatus status);

	List<CashShift> findTop20ByOrderByOpenedAtDesc();
}
