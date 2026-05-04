package com.stilov.springboot_practice_2503.users;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record UserCreateDTO(
        @NotNull
        String email,
        @NotNull
        String firstName,
        @NotNull
        String lastName,
        @FutureOrPresent
        LocalDateTime createdAt
) {
}
