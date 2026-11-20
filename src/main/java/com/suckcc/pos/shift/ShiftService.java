package com.suckcc.pos.shift;

import com.suckcc.pos.payment.PaymentRepository;
import com.suckcc.pos.returns.SaleReturnRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ShiftService {

	private final CashShiftRepository shifts;
	private final CashMovementRepository movements;
	private final PaymentRepository payments;
	private final SaleReturnRepository returns;

	public ShiftService(
			CashShiftRepository shifts,
			CashMovementRepository movements,
			PaymentRepository payments,
			SaleReturnRepository returns
	) {
		this.shifts = shifts;
		this.movements = movements;
		this.payments = payments;
		this.returns = returns;
	}

	public Optional<CashShift> findOpenShift() {
		return shifts.findFirstByStatusOrderByOpenedAtDesc(ShiftStatus.OPEN);
	}

	public List<CashShift> recentShifts() {
		return shifts.findTop20ByOrderByOpenedAtDesc();
	}

	public List<CashMovement> movementsFor(Long shiftId) {
		return movements.findByShiftIdOrderByCreatedAtDesc(shiftId);
	}

	public ShiftSummary summarize(CashShift shift) {
		long cashSales = payments.sumCashSalesForShift(shift.getId());
		long cashRefunds = returns.sumCashRefundsForShift(shift.getId());
		return ShiftSummary.calculate(
				shift.getOpeningFloatCents(),
				cashSales,
				cashRefunds,
				movementsFor(shift.getId()),
				shift.getCountedCashCents()
		);
	}

	@Transactional
	public CashShift openShift(Long userId, long openingFloatCents) {
		if (openingFloatCents < 0) {
			throw new IllegalArgumentException("El fondo inicial no puede ser negativo");
		}
		if (findOpenShift().isPresent()) {
			throw new IllegalStateException("Ya existe un turno de caja abierto");
		}
		CashShift shift = new CashShift();
		shift.setOpenedBy(userId);
		shift.setOpeningFloatCents(openingFloatCents);
		shift.setStatus(ShiftStatus.OPEN);
		return shifts.save(shift);
	}

	@Transactional
	public CashMovement recordMovement(Long userId, CashMovementType type, long amountCents, String reason) {
		if (amountCents <= 0) {
			throw new IllegalArgumentException("El monto debe ser mayor a cero");
		}
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("El motivo es obligatorio");
		}
		CashShift openShift = findOpenShift()
				.orElseThrow(() -> new IllegalStateException("No hay un turno abierto"));
		CashMovement movement = new CashMovement();
		movement.setShiftId(openShift.getId());
		movement.setType(type);
		movement.setAmountCents(amountCents);
		movement.setReason(reason.trim());
		movement.setCreatedBy(userId);
		return movements.save(movement);
	}

	@Transactional
	public CashShift closeOpenShift(Long userId, long countedCashCents, String notes) {
		if (countedCashCents < 0) {
			throw new IllegalArgumentException("El efectivo contado no puede ser negativo");
		}
		CashShift openShift = findOpenShift()
				.orElseThrow(() -> new IllegalStateException("No hay un turno abierto"));
		long cashSales = payments.sumCashSalesForShift(openShift.getId());
		long cashRefunds = returns.sumCashRefundsForShift(openShift.getId());
		ShiftSummary summary = ShiftSummary.calculate(
				openShift.getOpeningFloatCents(),
				cashSales,
				cashRefunds,
				movementsFor(openShift.getId()),
				countedCashCents
		);
		openShift.setClosedBy(userId);
		openShift.setClosedAt(OffsetDateTime.now());
		openShift.setCountedCashCents(countedCashCents);
		openShift.setExpectedCashCents(summary.expectedCashCents());
		openShift.setVarianceCents(summary.varianceCents());
		openShift.setNotes((notes == null || notes.isBlank()) ? null : notes.trim());
		openShift.setStatus(ShiftStatus.CLOSED);
		return shifts.save(openShift);
	}
}
