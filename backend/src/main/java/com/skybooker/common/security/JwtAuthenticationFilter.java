package com.skybooker.common.security;

import com.skybooker.admin.entity.AdminUser;
import com.skybooker.admin.mapper.AdminMapper;
import com.skybooker.auth.entity.User;
import com.skybooker.auth.mapper.AuthMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthMapper authMapper;
    private final AdminMapper adminMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            Claims claims = jwtTokenProvider.parseToken(token);

            Long userId = claims.get("userId", Long.class);
            String email = claims.get("email", String.class);
            String role = claims.get("role", String.class);
            String loginPortal = claims.get("loginPortal", String.class);

            if (!isAccountActive(userId, email, role, loginPortal)) {
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            LoginUserPrincipal principal = new LoginUserPrincipal(
                    userId,
                    email,
                    role,
                    loginPortal
            );

            var authority = new SimpleGrantedAuthority("ROLE_" + principal.role());
            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(authority));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private boolean isAccountActive(Long userId, String email, String role, String loginPortal) {
        if (userId == null || !StringUtils.hasText(email)
                || !StringUtils.hasText(role) || !StringUtils.hasText(loginPortal)) {
            return false;
        }

        User user = authMapper.findById(userId);
        if (user == null || "DISABLED".equals(user.getStatus())
                || !email.equals(user.getEmail()) || !role.equals(user.getRole())) {
            return false;
        }

        if ("USER".equals(loginPortal)) {
            return "USER".equals(role);
        }

        if ("ADMIN".equals(loginPortal)) {
            AdminUser adminUser = adminMapper.findByUserId(userId);
            return "ADMIN".equals(role)
                    && adminUser != null
                    && !"DISABLED".equals(adminUser.getStatus());
        }

        return false;
    }
}
