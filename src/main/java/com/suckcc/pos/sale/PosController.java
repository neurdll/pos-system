package com.suckcc.pos.sale;

import com.suckcc.pos.auth.PosUserPrincipal;
import com.suckcc.pos.catalog.ProductRepository;
import com.suckcc.pos.shared.Money;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.function.Supplier;

@Controller
public class PosController {

	private final CartService cartService;
	private final ProductRepository products;

	public PosController(CartService cartService, ProductRepository products) {
		this.cartService = cartService;
		this.products = products;
	}

	@GetMapping("/")
	public String home() {
		return "redirect:/pos";
	}

	@GetMapping("/pos")
	public String index(@RequestParam(defaultValue = "") String q, Model model) {
		CartService.CartView cart = cartService.currentCart().orElse(null);
		populateCartModel(model, cart);
		model.addAttribute("products", products.searchActive(q.trim()));
		model.addAttribute("q", q.trim());
		return "pos/index";
	}

	@GetMapping("/pos/products")
	public String searchProducts(@RequestParam(defaultValue = "") String q, Model model) {
		model.addAttribute("products", products.searchActive(q.trim()));
		return "pos/_catalog :: grid";
	}

	@PostMapping("/pos/cart/scan")
	public String scanCode(
			@RequestParam String code,
			Authentication authentication,
			Model model
	) {
		Long userId = PosUserPrincipal.requireId(authentication);
		return executeCartMutation(model, () -> cartService.addByCode(userId, code), "Artículo agregado");
	}

	@PostMapping("/pos/cart/items/{productId}")
	public String addProduct(
			@PathVariable Long productId,
			Authentication authentication,
			Model model
	) {
		Long userId = PosUserPrincipal.requireId(authentication);
		return executeCartMutation(model, () -> cartService.addProduct(userId, productId), "Artículo agregado");
	}

	@PostMapping("/pos/cart/items/{itemId}/qty")
	public String updateQty(
			@PathVariable Long itemId,
			@RequestParam(required = false) Integer qty,
			@RequestParam(required = false) Integer delta,
			Authentication authentication,
			Model model
	) {
		Long userId = PosUserPrincipal.requireId(authentication);
		return executeCartMutation(model, () -> cartService.adjustItemQty(userId, itemId, qty, delta), null);
	}

	@PostMapping("/pos/cart/discount")
	public String applyDiscount(
			@RequestParam String discount,
			Authentication authentication,
			Model model
	) {
		Long userId = PosUserPrincipal.requireId(authentication);
		return executeCartMutation(
				model,
				() -> cartService.applyDiscount(userId, Money.parseToCents(discount)),
				"Descuento actualizado"
		);
	}

	@PostMapping("/pos/cart/clear")
	public String clear(Authentication authentication, Model model) {
		Long userId = PosUserPrincipal.requireId(authentication);
		return executeCartMutation(model, () -> cartService.clearCart(userId), "Carrito limpiado");
	}

	private String executeCartMutation(Model model, Supplier<CartService.CartView> action, String successMessage) {
		try {
			CartService.CartView updated = action.get();
			populateCartModel(model, updated);
			if (successMessage != null) {
				model.addAttribute("message", successMessage);
				model.addAttribute("kind", "success");
				return "pos/_cart :: panelWithToast";
			}
			return "pos/_cart :: panel";
		} catch (IllegalArgumentException | IllegalStateException ex) {
			populateCartModel(model, cartService.currentCart().orElse(null));
			model.addAttribute("message", ex.getMessage());
			model.addAttribute("kind", "error");
			return "pos/_cart :: panelWithToast";
		}
	}

	private void populateCartModel(Model model, CartService.CartView cart) {
		model.addAttribute("openShift", cart != null ? cart.shift() : null);
		model.addAttribute("sale", cart != null ? cart.sale() : null);
		model.addAttribute("cartItems", cart != null ? cart.items() : java.util.List.of());
	}
}
