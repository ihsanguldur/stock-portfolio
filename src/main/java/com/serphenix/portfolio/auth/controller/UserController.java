package com.serphenix.portfolio.auth.controller;

import com.serphenix.portfolio.auth.dto.response.UserResponseDto;
import com.serphenix.portfolio.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponseDto findUser(Authentication authentication) {
        return userService.findUserByEmail(authentication.getName());
    }
}
