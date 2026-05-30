package com.skybooker.common.security;

public record LoginUserPrincipal(
        Long userId,
        String email,
        String role,
        String loginPortal
) {
}
