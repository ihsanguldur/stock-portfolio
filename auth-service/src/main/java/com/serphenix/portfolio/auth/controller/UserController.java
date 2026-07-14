package com.serphenix.portfolio.auth.controller;

import com.serphenix.portfolio.auth.dto.response.UserResponseDto;
import com.serphenix.portfolio.auth.service.UserService;
import com.serphenix.portfolio.security.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponseDto findUser(@AuthenticationPrincipal JwtPrincipal principal) {
        return userService.findUserByEmail(principal.email());
    }
}
