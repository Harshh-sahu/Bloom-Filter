package com.bloomfilter.bloomfilter.controller;

import com.bloomfilter.bloomfilter.dto.UsernameCheckResponse;
import com.bloomfilter.bloomfilter.exception.GlobalExceptionHandler;
import com.bloomfilter.bloomfilter.exception.ValidationException;
import com.bloomfilter.bloomfilter.service.UsernameService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UsernameController.class)
@Import(GlobalExceptionHandler.class)
class UsernameControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  UsernameService usernameService;

    @Test
    void checkAvailable_returns200WithAvailableTrue() throws Exception {
        when(usernameService.checkAvailability("alice"))
            .thenReturn(UsernameCheckResponse.available("alice"));

        mockMvc.perform(get("/api/v1/usernames/check").param("username", "alice"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(true))
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.checkedAt").exists());
    }

    @Test
    void checkTaken_returns200WithAvailableFalse() throws Exception {
        when(usernameService.checkAvailability("alice"))
            .thenReturn(UsernameCheckResponse.taken("alice"));

        mockMvc.perform(get("/api/v1/usernames/check").param("username", "alice"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void missingUsername_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/usernames/check"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void invalidUsername_returns400() throws Exception {
        when(usernameService.checkAvailability("a!b"))
            .thenThrow(new ValidationException("Invalid username"));

        mockMvc.perform(get("/api/v1/usernames/check").param("username", "a!b"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
