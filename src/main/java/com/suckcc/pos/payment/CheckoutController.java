package com.suckcc.pos.payment;

import com.suckcc.pos.auth.PosUserPrincipal;
import com.suckcc.pos.sale.CartService;
import com.suckcc.pos.shared.Money;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/pos/checkout")
public class CheckoutController {

	private final CartService cartService;
	private final CheckoutService checkoutService;

	public CheckoutController(CartService cartService, CheckoutService checkoutService) {
		this.cartService = cartService;
		this.checkoutService = checkoutService;
	}

	@GetMapping
	public String modal(Model model) {
		CartService.CartView cart = cartService.currentCart().orElse(null);
		if (cart == null || cart.sale() == null || cart.items().isEmpty()) {
			model.addAttribute("message", "Agrega al menos un artículo antes de cobrar");
			model.addAttribute("kind", "error");
			return "fragments/toast :: oob";
		}
		model.addAttribute("sale", cart.sale());
		return "pos/_checkout_modal :: modal";
	}

	@PostMapping
	public String submit(
			@RequestParam(value = "method", required = false) List<PaymentMethod> methods,
			@RequestParam(value = "amount", required = false) List<String> amounts,
			@RequestParam(value = "reference", required = false) List<String> references,
			Authentication authentication,
			HttpServletResponse response,
			Model model
	) {
		try {
			List<PaymentCalculator.TenderInput> tenders = parseTenders(methods, amounts, references);
			CheckoutService.CheckoutResult result =
					checkoutService.checkoutOpenSale(PosUserPrincipal.requireId(authentication), tenders);

			CartService.CartView refreshed = cartService.currentCart().orElse(null);
			model.addAttribute("openShift", refreshed != null ? refreshed.shift() : null);
			model.addAttribute("sale", null);
			model.addAttribute("cartItems", List.of());
			model.addAttribute("lastTicketId", result.sale().getId());
			model.addAttribute("lastTicketNumber", result.sale().getTicketNumber());
			model.addAttribute("lastChangeCents", result.changeCents());

			String toast = "Venta cobrada · " + result.sale().getTicketNumber()
					+ (result.changeCents() > 0 ? " · Cambio " + Money.formatCents(result.changeCents()) : "");
			model.addAttribute("message", toast);
			model.addAttribute("kind", "success");
			return "pos/_cart :: panelAfterCheckout";
		} catch (IllegalArgumentException | IllegalStateException ex) {
			response.setHeader("HX-Retarget", "#checkout-error");
			response.setHeader("HX-Reswap", "innerHTML");
			model.addAttribute("error", ex.getMessage());
			return "pos/_checkout_modal :: errorAlert";
		}
	}

	private static List<PaymentCalculator.TenderInput> parseTenders(
			List<PaymentMethod> methods,
			List<String> amounts,
			List<String> references
	) {
		if (methods == null || amounts == null) {
			return List.of();
		}
		int count = Math.min(methods.size(), amounts.size());
		List<PaymentCalculator.TenderInput> inputs = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			String rawAmount = amounts.get(i);
			if (rawAmount == null || rawAmount.isBlank()) {
				continue;
			}
			long cents = Money.parseToCents(rawAmount);
			String ref = (references != null && i < references.size()) ? references.get(i) : null;
			inputs.add(new PaymentCalculator.TenderInput(methods.get(i), cents, ref));
		}
		return inputs;
	}
}
