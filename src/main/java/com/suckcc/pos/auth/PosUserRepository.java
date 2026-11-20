package com.suckcc.pos.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PosUserRepository extends JpaRepository<PosUser, Long> {

	Optional<PosUser> findByUsernameAndActiveTrue(String username);

	List<PosUser> findByActiveTrueAndPinHashIsNotNull();
}
