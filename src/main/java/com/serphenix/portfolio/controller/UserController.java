package com.serphenix.portfolio.controller;

import com.serphenix.portfolio.dto.response.UserResponseDto;
import com.serphenix.portfolio.service.UserService;
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
