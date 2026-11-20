package com.suckcc.pos.payment;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure split-payment allocation and cash change computation.
 */
public final class PaymentCalculator {

	private PaymentCalculator() {
	}

	public record TenderInput(PaymentMethod method, long tenderedCents, String reference) {
	}

	public record AllocatedTender(
			PaymentMethod method,
			long amountCents,
			long tenderedCents,
			long changeCents,
			String reference
	) {
	}

	public record Allocation(
			List<AllocatedTender> lines,
			long totalTenderedCents,
			long totalChangeCents
	) {
	}

	public static Allocation allocate(long saleTotalCents, List<TenderInput> inputs) {
		if (saleTotalCents <= 0) {
			throw new IllegalArgumentException("El total de la venta debe ser mayor a cero");
		}
		if (inputs == null || inputs.isEmpty()) {
			throw new IllegalArgumentException("Agrega al menos un pago");
		}

		long nonCashTotal = 0L;
		long cashTendered = 0L;
		List<TenderInput> valid = new ArrayList<>();
		for (TenderInput input : inputs) {
			if (input.tenderedCents() < 0) {
				throw new IllegalArgumentException("El monto del pago no puede ser negativo");
			}
			if (input.tenderedCents() == 0) {
				continue;
			}
			valid.add(input);
			if (input.method() == PaymentMethod.CASH) {
				cashTendered += input.tenderedCents();
			} else {
				nonCashTotal += input.tenderedCents();
			}
		}

		if (valid.isEmpty()) {
			throw new IllegalArgumentException("Agrega al menos un pago mayor a cero");
		}
		if (nonCashTotal > saleTotalCents) {
			throw new IllegalArgumentException("Los pagos con tarjeta o transferencia no pueden exceder el total");
		}
		long totalTendered = nonCashTotal + cashTendered;
		if (totalTendered < saleTotalCents) {
			throw new IllegalArgumentException("El monto pagado es menor al total de la venta");
		}

		long totalChange = cashTendered - (saleTotalCents - nonCashTotal);
		long cashNeeded = saleTotalCents - nonCashTotal;
		long cashAssigned = 0L;
		int lastCashIndex = -1;
		for (int i = 0; i < valid.size(); i++) {
			if (valid.get(i).method() == PaymentMethod.CASH) {
				lastCashIndex = i;
			}
		}

		List<AllocatedTender> lines = new ArrayList<>(valid.size());
		for (int i = 0; i < valid.size(); i++) {
			TenderInput input = valid.get(i);
			String ref = (input.reference() == null || input.reference().isBlank()) ? null : input.reference().trim();
			if (input.method() != PaymentMethod.CASH) {
				lines.add(new AllocatedTender(input.method(), input.tenderedCents(), input.tenderedCents(), 0L, ref));
			} else {
				long remainingCashNeeded = Math.max(0L, cashNeeded - cashAssigned);
				long applied = Math.min(input.tenderedCents(), remainingCashNeeded);
				cashAssigned += applied;
				long lineChange = (i == lastCashIndex) ? totalChange : 0L;
				lines.add(new AllocatedTender(PaymentMethod.CASH, applied, input.tenderedCents(), lineChange, ref));
			}
		}
		return new Allocation(List.copyOf(lines), totalTendered, totalChange);
	}
}
