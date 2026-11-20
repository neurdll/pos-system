package com.suckcc.pos.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * Immutable session principal for the currently active POS operator.
 */
public record PosUserPrincipal(
		Long id,
		String username,
		String displayName,
		String passwordHash,
		Role role,
		boolean active
) implements UserDetails, Serializable {

	public static PosUserPrincipal from(PosUser user) {
		return new PosUserPrincipal(
				user.getId(),
				user.getUsername(),
				user.getDisplayName(),
				user.getPasswordHash(),
				user.getRole(),
				user.isActive()
		);
	}

	public static Long requireId(Authentication authentication) {
		if (authentication != null && authentication.getPrincipal() instanceof PosUserPrincipal principal) {
			return principal.id();
		}
		throw new IllegalStateException("Authenticated POS user principal required");
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public boolean isEnabled() {
		return active;
	}
}
