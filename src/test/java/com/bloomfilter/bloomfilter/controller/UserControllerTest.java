package com.bloomfilter.bloomfilter.controller;

import com.bloomfilter.bloomfilter.dto.UserCreateRequest;
import com.bloomfilter.bloomfilter.dto.UserCreateResponse;
import com.bloomfilter.bloomfilter.exception.GlobalExceptionHandler;
import com.bloomfilter.bloomfilter.exception.UsernameAlreadyTakenException;
import com.bloomfilter.bloomfilter.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;
    @MockBean  UserService   userService;

    @Test
    void createUser_returns201() throws Exception {
        UserCreateRequest req = new UserCreateRequest("alice123", "alice@example.com");
        UserCreateResponse resp = new UserCreateResponse(1L, "alice123", "alice@example.com",
            LocalDateTime.now().toString());

        when(userService.createUser(any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.username").value("alice123"));
    }

    @Test
    void createUser_duplicateUsername_returns409() throws Exception {
        UserCreateRequest req = new UserCreateRequest("alice123", "alice@example.com");
        when(userService.createUser(any())).thenThrow(new UsernameAlreadyTakenException("alice123"));

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    void createUser_invalidUsername_returns400() throws Exception {
        UserCreateRequest req = new UserCreateRequest("a!", "valid@example.com");

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
    }
}
