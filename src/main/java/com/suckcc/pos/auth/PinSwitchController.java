package com.suckcc.pos.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
public class PinSwitchController {

	private final PosUserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final SecurityContextRepository securityContextRepository;
	private final SecurityContextHolderStrategy contextHolderStrategy =
			SecurityContextHolder.getContextHolderStrategy();

	public PinSwitchController(
			PosUserRepository users,
			PasswordEncoder passwordEncoder,
			SecurityContextRepository securityContextRepository
	) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.securityContextRepository = securityContextRepository;
	}

	@GetMapping("/login")
	public String login() {
		return "login";
	}

	@GetMapping("/auth/pin-modal")
	public String pinModal() {
		return "auth/_pin_modal :: modal";
	}

	@PostMapping("/auth/switch-pin")
	public String switchByPin(
			@RequestParam String pin,
			@RequestHeader(value = "HX-Request", required = false) String hxRequest,
			HttpServletRequest request,
			HttpServletResponse response,
			Model model
	) {
		Optional<PosUser> matched = findByPin(pin);
		if (matched.isEmpty()) {
			if (hxRequest != null) {
				model.addAttribute("error", "PIN inválido");
				return "auth/_pin_modal :: modal";
			}
			return "redirect:/pos?pinError";
		}

		PosUserPrincipal principal = PosUserPrincipal.from(matched.get());
		Authentication newAuth = UsernamePasswordAuthenticationToken.authenticated(
				principal, null, principal.getAuthorities());
		SecurityContext context = contextHolderStrategy.createEmptyContext();
		context.setAuthentication(newAuth);
		contextHolderStrategy.setContext(context);
		securityContextRepository.saveContext(context, request, response);

		if (hxRequest != null) {
			response.setHeader("HX-Refresh", "true");
			model.addAttribute("message", "Cajero activo: " + principal.displayName());
			model.addAttribute("kind", "success");
			return "fragments/toast :: oob";
		}
		return "redirect:/pos";
	}

	private Optional<PosUser> findByPin(String rawPin) {
		if (rawPin == null || rawPin.isBlank()) {
			return Optional.empty();
		}
		String trimmed = rawPin.trim();
		return users.findByActiveTrueAndPinHashIsNotNull().stream()
				.filter(user -> passwordEncoder.matches(trimmed, user.getPinHash()))
				.findFirst();
	}
}
