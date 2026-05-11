package com.cryptotrackr.controller;

import com.cryptotrackr.domain.User;
import com.cryptotrackr.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean UserService userService;

    @Test
    void shouldReturn201WhenUserCreatedSuccessfully() throws Exception {
        when(userService.createUser("bivar", "bivar@example.com"))
                .thenReturn(User.builder().id(1L).username("bivar").email("bivar@example.com").build());

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bivar\",\"email\":\"bivar@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("bivar"))
                .andExpect(jsonPath("$.email").value("bivar@example.com"))
                .andExpect(jsonPath("$.wallets").doesNotExist());
    }

    @Test
    void shouldReturn400WhenUsernameIsBlank() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"email\":\"bivar@example.com\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenEmailIsInvalid() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bivar\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnUserWhenFound() throws Exception {
        when(userService.getUser(1L))
                .thenReturn(User.builder().id(1L).username("bivar").email("bivar@example.com").build());

        mockMvc.perform(get("/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("bivar"))
                .andExpect(jsonPath("$.email").value("bivar@example.com"))
                .andExpect(jsonPath("$.wallets").doesNotExist());
    }

    @Test
    void shouldReturn404WhenUserNotFound() throws Exception {
        when(userService.getUser(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: 99"));

        mockMvc.perform(get("/users/99"))
                .andExpect(status().isNotFound());
    }
}
