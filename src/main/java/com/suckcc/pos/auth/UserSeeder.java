package com.suckcc.pos.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds initial ADMIN and CASHIER accounts when the users table is empty.
 */
@Component
public class UserSeeder implements ApplicationRunner {

	private final PosUserRepository users;
	private final PasswordEncoder passwordEncoder;

	public UserSeeder(PosUserRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (users.count() > 0) {
			return;
		}
		users.saveAll(List.of(
				new PosUser(
						"admin",
						"Administrador",
						passwordEncoder.encode("admin123"),
						passwordEncoder.encode("1234"),
						Role.ADMIN
				),
				new PosUser(
						"cajero1",
						"Cajero Turno 1",
						passwordEncoder.encode("cajero123"),
						passwordEncoder.encode("0000"),
						Role.CASHIER
				)
		));
	}
}
