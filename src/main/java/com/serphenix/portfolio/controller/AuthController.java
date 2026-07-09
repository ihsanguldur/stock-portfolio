package com.serphenix.portfolio.controller;

import com.serphenix.portfolio.dto.request.LoginRequestDto;
import com.serphenix.portfolio.dto.request.LogoutRequestDto;
import com.serphenix.portfolio.dto.request.RefreshRequestDto;
import com.serphenix.portfolio.dto.request.RegisterRequestDto;
import com.serphenix.portfolio.dto.response.LoginResponseDto;
import com.serphenix.portfolio.dto.response.RefreshResponseDto;
import com.serphenix.portfolio.dto.response.RegisterResponseDto;
import com.serphenix.portfolio.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponseDto register(@Valid @RequestBody RegisterRequestDto request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public LoginResponseDto login(@Valid @RequestBody LoginRequestDto request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public RefreshResponseDto refresh(@Valid @RequestBody RefreshRequestDto request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequestDto request) {
        authService.logout(request);
    }
}
