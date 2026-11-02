package com.ahmtcnmn.manager.security;

import com.ahmtcnmn.manager.service.User.JwtAuthFilter;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Geçici yapılandırma: gerçek API key / JWT auth (Faz 3/9) yazılana kadar
 * REST API'yi kullanılabilir tutar. CSRF, session-based form login için
 * anlamlıdır — stateless JSON API'de gereksizdir ve POST/PUT/DELETE
 * isteklerini reddeder.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CustomAuthenticationEntryPoint entryPoint) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))   // ← yeni satır
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login","/api/health").permitAll()
                // Agent endpoint'leri: JWT yerine ApiKeyInterceptor korur (WebConfig). Panelin okuduğu
                // GET /api/events, /api/events/{id}/... JWT ister; bu yüzden yalnız /create açık.
                .requestMatchers("/api/agents/heartbeat", "/api/events/create", "/api/commands/**", "/api/agents/register").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    @Bean 
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
