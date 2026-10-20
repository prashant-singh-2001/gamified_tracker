package com.tracker.gateway.repository;

import com.tracker.gateway.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository <User,Long>{
Optional<User> findByEmail(String email);

// Callers pass an already-normalised email (see Emails.normalize); rows are stored normalised.
boolean existsByEmail(String email);

}
