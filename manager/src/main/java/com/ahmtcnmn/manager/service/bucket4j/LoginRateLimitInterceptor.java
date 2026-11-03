package com.ahmtcnmn.manager.service.bucket4j;

import java.io.IOException;
import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.ahmtcnmn.manager.common.exception.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LoginRateLimitInterceptor implements HandlerInterceptor{

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws JsonProcessingException, IOException{
        String clientIp = request.getRemoteAddr();
        // Login şifre denemesine karşı sıkı (5/dk); refresh token tahmin edilemez, ayrı ve geniş kova (30/dk)
        boolean isRefresh = request.getRequestURI().endsWith("/refresh");
        Bucket bucket = isRefresh
                ? rateLimiterService.resolveBucket("refresh:" + clientIp, 30, Duration.ofMinutes(1))
                : rateLimiterService.resolveBucket("login:" + clientIp, 5, Duration.ofMinutes(1));
        if(bucket.tryConsume(1)){
            return true;
        }else{
            response.setStatus(429);
            response.setContentType("application/json");

            ErrorResponse body = ErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                "Çok fazla istek gönderildi, lütfen bekleyin", request.getRequestURI()
            );
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return false;
        }
    }
}
