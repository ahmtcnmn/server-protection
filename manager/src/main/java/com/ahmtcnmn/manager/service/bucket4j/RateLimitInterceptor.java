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
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws JsonProcessingException, IOException  {
        String apiKey = request.getHeader("X-API-KEY");
        if (apiKey == null) {
            return true;   // API key yoksa zaten ApiKeyInterceptor reddedecek, burada uğraşmaya gerek yok
        }

        // Event'ler ve kontrol trafiği (heartbeat, komut, decision stream) ayrı kovalarda:
        // saldırı anında yağan event'ler kovayı bitirip ban kararlarının Agent'a ulaşmasını geciktirmesin
        boolean isEvent = request.getRequestURI().startsWith("/api/events/");
        Bucket bucket = rateLimiterService.resolveBucket(apiKey + (isEvent ? ":events" : ":control"), 60, Duration.ofMinutes(1));
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
        // 📝 tryConsume çağırıp, başarısızsa 429 dönün
        // 📝 response.setContentType("application/json") + ErrorResponse.of(...) yazmayı unutmayın (tutarlı format için)
    }
}