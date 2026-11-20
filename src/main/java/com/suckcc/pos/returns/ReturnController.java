package com.suckcc.pos.returns;

import com.suckcc.pos.auth.PosUserPrincipal;
import com.suckcc.pos.payment.PaymentMethod;
import com.suckcc.pos.sale.Sale;
import com.suckcc.pos.sale.SaleRepository;
import com.suckcc.pos.shift.ShiftService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/returns")
public class ReturnController {

	private final ReturnService returnService;
	private final SaleRepository sales;
	private final ShiftService shiftService;

	public ReturnController(ReturnService returnService, SaleRepository sales, ShiftService shiftService) {
		this.returnService = returnService;
		this.sales = sales;
		this.shiftService = shiftService;
	}

	@GetMapping("/new")
	public String form(@RequestParam Long saleId, Model model) {
		Sale sale = sales.findById(saleId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket no encontrado"));
		model.addAttribute("sale", sale);
		model.addAttribute("lines", returnService.returnableLines(saleId));
		model.addAttribute("methods", PaymentMethod.values());
		model.addAttribute("openShift", shiftService.findOpenShift().orElse(null));
		return "returns/form";
	}

	@PostMapping
	public String submit(
			@RequestParam Long saleId,
			@RequestParam PaymentMethod refundMethod,
			@RequestParam String reason,
			@RequestParam(value = "saleItemId", required = false) List<Long> saleItemIds,
			@RequestParam(value = "qty", required = false) List<Integer> qtys,
			@RequestParam(value = "restockId", required = false) List<Long> restockIds,
			Authentication authentication,
			RedirectAttributes redirectAttributes
	) {
		try {
			Set<Long> restockSet = restockIds == null ? Set.of() : new HashSet<>(restockIds);
			List<ReturnService.ReturnLineRequest> lines = new ArrayList<>();
			if (saleItemIds != null && qtys != null) {
				int count = Math.min(saleItemIds.size(), qtys.size());
				for (int i = 0; i < count; i++) {
					Long itemId = saleItemIds.get(i);
					Integer q = qtys.get(i);
					if (q != null && q > 0) {
						lines.add(new ReturnService.ReturnLineRequest(itemId, q, restockSet.contains(itemId)));
					}
				}
			}
			returnService.processReturn(PosUserPrincipal.requireId(authentication), saleId, refundMethod, reason, lines);
			redirectAttributes.addFlashAttribute("success", "Devolución registrada correctamente");
			return "redirect:/tickets";
		} catch (IllegalArgumentException | IllegalStateException ex) {
			redirectAttributes.addFlashAttribute("error", ex.getMessage());
			return "redirect:/returns/new?saleId=" + saleId;
		}
	}
}
