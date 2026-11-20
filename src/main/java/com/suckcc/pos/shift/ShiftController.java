package com.suckcc.pos.shift;

import com.suckcc.pos.auth.PosUserPrincipal;
import com.suckcc.pos.shared.Money;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
@RequestMapping("/shifts")
public class ShiftController {

	private final ShiftService shiftService;

	public ShiftController(ShiftService shiftService) {
		this.shiftService = shiftService;
	}

	@GetMapping
	public String index(Model model) {
		populateModel(model);
		return "shift/index";
	}

	@PostMapping("/open")
	public String open(
			@RequestParam String openingFloat,
			Authentication authentication
	) {
		long floatCents = Money.parseToCents(openingFloat);
		shiftService.openShift(PosUserPrincipal.requireId(authentication), floatCents);
		return "redirect:/shifts?opened";
	}

	@PostMapping("/movements")
	public String addMovement(
			@RequestParam CashMovementType type,
			@RequestParam String amount,
			@RequestParam String reason,
			@RequestHeader(value = "HX-Request", required = false) String hxRequest,
			Authentication authentication,
			Model model
	) {
		long amountCents = Money.parseToCents(amount);
		shiftService.recordMovement(PosUserPrincipal.requireId(authentication), type, amountCents, reason);
		if (hxRequest != null) {
			populateModel(model);
			model.addAttribute("message", "Movimiento registrado");
			model.addAttribute("kind", "success");
			return "shift/_panel :: panelWithToast";
		}
		return "redirect:/shifts";
	}

	@PostMapping("/close")
	public String close(
			@RequestParam String countedCash,
			@RequestParam(required = false) String notes,
			Authentication authentication
	) {
		long countedCents = Money.parseToCents(countedCash);
		shiftService.closeOpenShift(PosUserPrincipal.requireId(authentication), countedCents, notes);
		return "redirect:/shifts?closed";
	}

	private void populateModel(Model model) {
		Optional<CashShift> openShift = shiftService.findOpenShift();
		model.addAttribute("openShift", openShift.orElse(null));
		if (openShift.isPresent()) {
			CashShift shift = openShift.get();
			model.addAttribute("summary", shiftService.summarize(shift));
			model.addAttribute("movements", shiftService.movementsFor(shift.getId()));
		}
		model.addAttribute("recentShifts", shiftService.recentShifts());
	}
}
