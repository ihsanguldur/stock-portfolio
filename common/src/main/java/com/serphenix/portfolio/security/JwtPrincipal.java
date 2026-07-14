package com.serphenix.portfolio.security;

import java.security.Principal;

public record JwtPrincipal(Long userId, String email) implements Principal {
    @Override
    public String getName() {
        return email;
    }
}
