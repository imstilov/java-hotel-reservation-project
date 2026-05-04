package com.stilov.springboot_practice_2503.users;

import com.stilov.springboot_practice_2503.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    List<UserEntity> findAll();

    boolean existsByEmail(String email);
}

