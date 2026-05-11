package com.cryptotrackr.repository;

import com.cryptotrackr.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
