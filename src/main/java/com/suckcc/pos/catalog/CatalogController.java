package com.suckcc.pos.catalog;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

@Controller
@RequestMapping("/catalog")
public class CatalogController {

	private final ProductRepository products;

	public CatalogController(ProductRepository products) {
		this.products = products;
	}

	@GetMapping
	public String list(
			@RequestParam(defaultValue = "") String q,
			@RequestHeader(value = "HX-Target", required = false) String hxTarget,
			Model model
	) {
		String query = q.trim();
		model.addAttribute("products", products.search(query));
		model.addAttribute("q", query);
		if ("product-rows".equals(hxTarget)) {
			return "catalog/_table :: rows";
		}
		return "catalog/list";
	}

	@GetMapping("/new")
	public String newForm(Model model) {
		model.addAttribute("productForm", ProductForm.empty());
		model.addAttribute("productId", null);
		return "catalog/form";
	}

	@PostMapping
	public String create(
			@Valid @ModelAttribute("productForm") ProductForm form,
			BindingResult binding,
			Model model
	) {
		validateUniqueCodes(form, null, binding);
		if (binding.hasErrors()) {
			model.addAttribute("productId", null);
			return "catalog/form";
		}
		Product product = new Product();
		form.applyTo(product);
		products.save(product);
		return "redirect:/catalog?saved";
	}

	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Long id, Model model) {
		Product product = findOrThrow(id);
		model.addAttribute("productForm", ProductForm.from(product));
		model.addAttribute("productId", id);
		return "catalog/form";
	}

	@PostMapping("/{id}")
	public String update(
			@PathVariable Long id,
			@Valid @ModelAttribute("productForm") ProductForm form,
			BindingResult binding,
			Model model
	) {
		Product product = findOrThrow(id);
		validateUniqueCodes(form, id, binding);
		if (binding.hasErrors()) {
			model.addAttribute("productId", id);
			return "catalog/form";
		}
		form.applyTo(product);
		product.setUpdatedAt(OffsetDateTime.now());
		products.save(product);
		return "redirect:/catalog?saved";
	}

	@PostMapping("/{id}/toggle")
	public String toggleActive(
			@PathVariable Long id,
			@RequestHeader(value = "HX-Request", required = false) String hxRequest,
			Model model
	) {
		Product product = findOrThrow(id);
		product.setActive(!product.isActive());
		product.setUpdatedAt(OffsetDateTime.now());
		products.save(product);
		if (hxRequest != null) {
			model.addAttribute("p", product);
			model.addAttribute("message", product.isActive() ? "Producto activado" : "Producto desactivado");
			model.addAttribute("kind", "success");
			return "catalog/_table :: toggledRow";
		}
		return "redirect:/catalog";
	}

	private Product findOrThrow(Long id) {
		return products.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
	}

	private void validateUniqueCodes(ProductForm form, Long currentId, BindingResult binding) {
		if (form.sku() != null && !form.sku().isBlank()) {
			String sku = form.sku().trim();
			boolean exists = currentId == null
					? products.existsBySkuIgnoreCase(sku)
					: products.existsBySkuIgnoreCaseAndIdNot(sku, currentId);
			if (exists) {
				binding.rejectValue("sku", "duplicate", "Ya existe un producto con ese SKU");
			}
		}
		if (form.barcode() != null && !form.barcode().isBlank()) {
			String barcode = form.barcode().trim();
			boolean exists = currentId == null
					? products.existsByBarcode(barcode)
					: products.existsByBarcodeAndIdNot(barcode, currentId);
			if (exists) {
				binding.rejectValue("barcode", "duplicate", "Ya existe un producto con ese código de barras");
			}
		}
	}
}
