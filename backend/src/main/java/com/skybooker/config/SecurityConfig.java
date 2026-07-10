package com.skybooker.config;

import com.skybooker.common.security.JwtAuthenticationFilter;
import com.skybooker.common.security.LoginUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.function.Supplier;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityExceptionHandler securityExceptionHandler;

    @Value("${springdoc.api-docs.enabled:false}")
    private boolean openApiEnabled;

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String[] corsAllowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler)
                )
                .authorizeHttpRequests(auth -> {
                    auth
                            .requestMatchers("/api/auth/login").permitAll()
                            .requestMatchers("/api/auth/email-code").permitAll()
                            .requestMatchers("/api/auth/register").permitAll()
                            .requestMatchers("/api/auth/reset-password").permitAll()
                            .requestMatchers("/api/auth/refresh").permitAll()
                            .requestMatchers("/api/auth/logout").permitAll()
                            .requestMatchers("/api/admin/auth/login").permitAll()
                            .requestMatchers("/api/admin/auth/refresh").permitAll()
                            .requestMatchers("/api/admin/logout").permitAll()
                            .requestMatchers("/api/flights/**").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/itineraries/search").permitAll()
                            .requestMatchers("/api/itineraries/quote").access((authSupplier, ctx) -> requireUserPortal(authSupplier))
                            .requestMatchers("/api/ai/**").access(
                                    (authSupplier, ctx) -> checkAiAccess(authSupplier))
                            .requestMatchers("/api/orders/**", "/api/passengers/**", "/api/waitlist/**")
                            .access((authSupplier, ctx) -> requireUserPortal(authSupplier))
                            .requestMatchers("/api/auth/me", "/api/auth/account")
                            .access((authSupplier, ctx) -> requireUserPortal(authSupplier))
                            // /api/admin/** 通配收敛为 ADMIN portal，覆盖本变更新增的订单/用户维护端点。
                            .requestMatchers("/api/admin/**")
                            .access((authSupplier, ctx) -> requireAdminPortal(authSupplier));

                    if (openApiEnabled) {
                        auth
                                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                                .requestMatchers("/doc.html", "/webjars/**", "/swagger-resources/**").permitAll();
                    }

                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private AuthorizationDecision checkAiAccess(Supplier<Authentication> authentication) {
        Authentication a = authentication.get();
        if (a == null || a instanceof AnonymousAuthenticationToken) {
            return new AuthorizationDecision(true);
        }
        if (a instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof LoginUserPrincipal p) {
            return new AuthorizationDecision(
                    "USER".equals(p.role()) && "USER".equals(p.loginPortal()));
        }
        return new AuthorizationDecision(false);
    }

    private AuthorizationDecision requireUserPortal(Supplier<Authentication> authentication) {
        Authentication a = authentication.get();
        if (a instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof LoginUserPrincipal p) {
            return new AuthorizationDecision(
                    "USER".equals(p.role()) && "USER".equals(p.loginPortal()));
        }
        return new AuthorizationDecision(false);
    }

    private AuthorizationDecision requireAdminPortal(Supplier<Authentication> authentication) {
        Authentication a = authentication.get();
        if (a instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof LoginUserPrincipal p) {
            return new AuthorizationDecision(
                    "ADMIN".equals(p.role()) && "ADMIN".equals(p.loginPortal()));
        }
        return new AuthorizationDecision(false);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 显式 origin 列表（不可用 "*"，因 allowCredentials=true）；逗号分隔，来自 app.cors.allowed-origins
        config.setAllowedOrigins(List.of(corsAllowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
