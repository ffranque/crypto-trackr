package com.cryptotrackr.controller;

import com.cryptotrackr.dto.request.CreateUserRequest;
import com.cryptotrackr.dto.response.UserResponse;
import com.cryptotrackr.domain.User;
import com.cryptotrackr.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.createUser(request.username(), request.email());
        return toResponse(user);
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Long id) {
        return toResponse(userService.getUser(id));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getCreatedAt());
    }
}
