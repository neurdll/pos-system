package com.suckcc.pos.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProductRepository products;

	@Test
	@WithMockUser(username = "admin", roles = "ADMIN")
	void adminCanCreateSearchUpdateAndToggleProduct() throws Exception {
		mockMvc.perform(post("/catalog")
						.with(csrf())
						.param("sku", "CAFE-001")
						.param("barcode", "750100000001")
						.param("name", "Café Americano 12oz")
						.param("category", "Bebidas")
						.param("price", "45.50")
						.param("taxPercent", "16")
						.param("stockQty", "25")
						.param("trackInventory", "true")
						.param("active", "true"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/catalog?saved"));

		Product saved = products.findActiveByCode("750100000001").orElseThrow();
		assertThat(saved.getPriceCents()).isEqualTo(4550L);
		assertThat(saved.getTaxRateBps()).isEqualTo(1600);

		// HTMX live search returns rows fragment
		MvcResult searchRes = mockMvc.perform(get("/catalog")
						.param("q", "CAFE-001")
						.header("HX-Target", "product-rows"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(searchRes.getResponse().getContentAsString())
				.contains("Café Americano 12oz")
				.contains("$45.50")
				.doesNotContain("<html");

		// Duplicate SKU shows validation error instead of 500
		MvcResult dupRes = mockMvc.perform(post("/catalog")
						.with(csrf())
						.param("sku", "CAFE-001")
						.param("name", "Duplicado")
						.param("price", "10.00")
						.param("taxPercent", "16")
						.param("stockQty", "1")
						.param("trackInventory", "true")
						.param("active", "true"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(dupRes.getResponse().getContentAsString()).contains("Ya existe un producto con ese SKU");

		// Inline HTMX toggle switches active -> false and returns toast OOB
		MvcResult toggleRes = mockMvc.perform(post("/catalog/{id}/toggle", saved.getId())
						.with(csrf())
						.header("HX-Request", "true"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(toggleRes.getResponse().getContentAsString())
				.contains("Inactivo")
				.contains("Producto desactivado");
		assertThat(products.findById(saved.getId()).orElseThrow().isActive()).isFalse();
	}

	@Test
	@WithMockUser(username = "cajero1", roles = "CASHIER")
	void cashierCanReadCatalogButCannotMutate() throws Exception {
		mockMvc.perform(get("/catalog"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/catalog/new"))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/catalog")
						.with(csrf())
						.param("sku", "BLOCKED")
						.param("name", "No permitido")
						.param("price", "10.00")
						.param("taxPercent", "16")
						.param("stockQty", "1"))
				.andExpect(status().isForbidden());
	}
}
