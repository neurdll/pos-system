package com.suckcc.pos.ticket;

import com.suckcc.pos.auth.PosUser;
import com.suckcc.pos.auth.PosUserRepository;
import com.suckcc.pos.payment.Payment;
import com.suckcc.pos.payment.PaymentRepository;
import com.suckcc.pos.sale.Sale;
import com.suckcc.pos.sale.SaleItemRepository;
import com.suckcc.pos.sale.SaleRepository;
import com.suckcc.pos.sale.SaleStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Controller
@RequestMapping("/tickets")
public class TicketController {

	private final SaleRepository sales;
	private final SaleItemRepository saleItems;
	private final PaymentRepository payments;
	private final PosUserRepository users;

	public TicketController(
			SaleRepository sales,
			SaleItemRepository saleItems,
			PaymentRepository payments,
			PosUserRepository users
	) {
		this.sales = sales;
		this.saleItems = saleItems;
		this.payments = payments;
		this.users = users;
	}

	@GetMapping
	public String list(
			@RequestParam(defaultValue = "") String q,
			@RequestHeader(value = "HX-Target", required = false) String hxTarget,
			Model model
	) {
		String query = q.trim();
		model.addAttribute("tickets", sales.searchCompletedTickets(query));
		model.addAttribute("q", query);
		if ("ticket-rows".equals(hxTarget)) {
			return "tickets/_table :: rows";
		}
		return "tickets/list";
	}

	@GetMapping("/{id}/print")
	public String print(@PathVariable Long id, Model model) {
		Sale sale = sales.findById(id)
				.filter(s -> s.getStatus() != SaleStatus.OPEN)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket no encontrado"));

		List<Payment> salePayments = payments.findBySaleIdOrderByIdAsc(sale.getId());
		long totalTendered = salePayments.stream().mapToLong(Payment::getTenderedCents).sum();
		long totalChange = salePayments.stream().mapToLong(Payment::getChangeCents).sum();
		String cashierName = users.findById(sale.getCashierId())
				.map(PosUser::getDisplayName)
				.orElse("Cajero #" + sale.getCashierId());

		model.addAttribute("sale", sale);
		model.addAttribute("items", saleItems.findBySaleIdOrderByIdAsc(sale.getId()));
		model.addAttribute("payments", salePayments);
		model.addAttribute("totalTenderedCents", totalTendered);
		model.addAttribute("totalChangeCents", totalChange);
		model.addAttribute("cashierName", cashierName);
		return "tickets/print";
	}
}
