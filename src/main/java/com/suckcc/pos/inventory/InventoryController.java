package com.suckcc.pos.inventory;

import com.suckcc.pos.auth.PosUserPrincipal;
import com.suckcc.pos.catalog.ProductRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/inventory")
public class InventoryController {

	private final InventoryService inventoryService;
	private final ProductRepository products;

	public InventoryController(InventoryService inventoryService, ProductRepository products) {
		this.inventoryService = inventoryService;
		this.products = products;
	}

	@GetMapping
	public String index(
			@RequestParam(required = false) Long productId,
			@RequestParam(required = false) StockMovementType type,
			@RequestHeader(value = "HX-Target", required = false) String hxTarget,
			Model model
	) {
		model.addAttribute("movements", inventoryService.ledger(productId, type));
		model.addAttribute("products", products.search(""));
		model.addAttribute("selectedProductId", productId);
		model.addAttribute("selectedType", type);
		model.addAttribute("types", StockMovementType.values());
		if ("kardex-rows".equals(hxTarget)) {
			return "inventory/_kardex :: rows";
		}
		return "inventory/index";
	}

	@PostMapping("/adjust")
	public String adjust(
			@RequestParam Long productId,
			@RequestParam StockMovementType type,
			@RequestParam int qtyDelta,
			@RequestParam(required = false) String note,
			Authentication authentication,
			RedirectAttributes redirectAttributes
	) {
		try {
			inventoryService.adjustStock(PosUserPrincipal.requireId(authentication), productId, type, qtyDelta, note);
			redirectAttributes.addFlashAttribute("success", "Movimiento de inventario registrado");
		} catch (IllegalArgumentException ex) {
			redirectAttributes.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/inventory";
	}
}
