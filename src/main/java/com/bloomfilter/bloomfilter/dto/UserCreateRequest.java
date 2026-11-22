package com.bloomfilter.bloomfilter.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(
        regexp = "^[a-zA-Z0-9._]+$",
        message = "Username may only contain letters, digits, dots, or underscores"
    )
    String username,

    @Email(message = "Invalid email address")
    String email
) {}
