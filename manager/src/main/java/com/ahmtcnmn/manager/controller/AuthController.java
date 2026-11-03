package com.ahmtcnmn.manager.controller;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ahmtcnmn.manager.common.RestBaseController;
import com.ahmtcnmn.manager.common.exceptionController.RootEntity;
import com.ahmtcnmn.manager.dto.User.DtoLoginRequest;
import com.ahmtcnmn.manager.dto.User.DtoLoginResponse;
import com.ahmtcnmn.manager.service.User.JwtUtil;
import com.ahmtcnmn.manager.service.User.RefreshTokenService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController extends RestBaseController  {

    // Cookie yalnız /api/auth isteklerine gider; diğer API çağrılarında taşınmaz
    private static final String REFRESH_COOKIE = "sentinel_refresh";
    private static final String COOKIE_PATH = "/api/auth";

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.jwt.refresh-cookie-secure:true}")
    private boolean secureCookie;

    /** Kullanıcı adı + şifre → kısa ömürlü access token (gövdede) + refresh token (httpOnly cookie). */
    @PostMapping("/login")
    public RootEntity<DtoLoginResponse> login(@Valid @RequestBody DtoLoginRequest request, HttpServletResponse response) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );
        String refresh = refreshTokenService.issue(request.username());
        setRefreshCookie(response, refresh, refreshTokenService.lifetime());
        return ok(new DtoLoginResponse(jwtUtil.generateToken(request.username())));
    }

    /** Cookie'deki refresh token → yeni access token. Refresh token da yenilenir (rotation). */
    @PostMapping("/refresh")
    public RootEntity<DtoLoginResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        RefreshTokenService.Issued issued = refreshTokenService.rotate(refreshToken);
        setRefreshCookie(response, issued.rawToken(), refreshTokenService.lifetime());
        return ok(new DtoLoginResponse(jwtUtil.generateToken(issued.username())));
    }

    /** Refresh token'ı iptal eder ve cookie'yi siler. Access token süresi dolana kadar (en fazla 15 dk) geçerli kalır. */
    @PostMapping("/logout")
    public RootEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        refreshTokenService.revoke(refreshToken);
        setRefreshCookie(response, "", Duration.ZERO);
        return ok();
    }

    private void setRefreshCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)          // JavaScript okuyamaz → XSS ile çalınamaz
                .secure(secureCookie)
                .sameSite("Strict")      // başka sitelerden gelen isteklerle gönderilmez (CSRF)
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
