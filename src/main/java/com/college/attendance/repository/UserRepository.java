package com.college.attendance.repository;

import com.college.attendance.model.Role;
import com.college.attendance.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findByRoleOrderByName(Role role);

    long countByRole(Role role);

    boolean existsByEmailIgnoreCase(String email);
}
