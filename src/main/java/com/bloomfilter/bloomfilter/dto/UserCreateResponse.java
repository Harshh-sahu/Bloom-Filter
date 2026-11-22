package com.bloomfilter.bloomfilter.dto;

import com.bloomfilter.bloomfilter.model.User;

public record UserCreateResponse(
    Long id,
    String username,
    String email,
    String createdAt
) {
    public static UserCreateResponse from(User user) {
        return new UserCreateResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getCreatedAt().toString()
        );
    }
}
