package com.ahmtcnmn.manager.common.handler;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.ahmtcnmn.manager.common.exception.ErrorResponse;
import com.ahmtcnmn.manager.common.exception.MessageType;
import com.ahmtcnmn.manager.common.exceptionController.AppException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1) Kendi exception'larımız: AppException ve tüm alt tipleri.
    // Her exception kendi HttpStatus ve code'unu taşıdığı için tek handler yeterli.
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex, WebRequest request) {
        log.warn("AppException: code={}, message={}", ex.getCode(), ex.getMessage());
        ErrorResponse body = ErrorResponse.of(ex.getHttpStatus(), ex.getCode(), ex.getMessage(), path(request));
        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    // 2) @Valid doğrulama hataları: hangi alanın neden geçersiz olduğunu topla.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        log.warn("Validation failed: {}", errors);
        ErrorResponse body = ErrorResponse.of(
                MessageType.EVENT_VALIDATION_FAILED.getHttpStatus(),
                MessageType.EVENT_VALIDATION_FAILED.name(),
                errors,
                path(request));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // Path/query parametre hataları da 400'e düşer; detay sadece loga yazılır.
    // HttpMessageNotReadableException: bozuk JSON gövdesi ya da yanlış tipte alan (500 yerine 400)
    @ExceptionHandler({ MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class })
    public ResponseEntity<ErrorResponse> handleBadRequest(java.lang.Exception ex, WebRequest request) {
        log.warn("Bad request at {}: {}", path(request), ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST, "BAD_REQUEST", "İstek parametreleri geçersiz", path(request));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // Olmayan adres (ör. tarayıcının istediği /favicon.ico) 500 değil 404; yanlış HTTP metodu 405.
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(java.lang.Exception ex, WebRequest request) {
        ErrorResponse body = ErrorResponse.of(HttpStatus.NOT_FOUND, "NOT_FOUND", "Adres bulunamadı", path(request));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(java.lang.Exception ex, WebRequest request) {
        ErrorResponse body = ErrorResponse.of(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage(), path(request));
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    // 3) Kimlik/yetki hataları (Spring Security).
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex, WebRequest request) {
        log.warn("Authentication failed: {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
                MessageType.INVALID_API_KEY.getHttpStatus(),
                MessageType.INVALID_API_KEY.name(),
                MessageType.INVALID_API_KEY.getMessage(),
                path(request));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
                MessageType.ACCESS_DENIED.getHttpStatus(),
                MessageType.ACCESS_DENIED.name(),
                MessageType.ACCESS_DENIED.getMessage(),
                path(request));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    // 4) Son çare: yakalanmamış her şeyi 500'e çevir, detayı istemciye sızdırma.
    @ExceptionHandler(java.lang.Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(java.lang.Exception ex, WebRequest request) {
        log.error("Unexpected error at {}", path(request), ex);
        ErrorResponse body = ErrorResponse.of(
                MessageType.INTERNAL_ERROR.getHttpStatus(),
                MessageType.INTERNAL_ERROR.name(),
                MessageType.INTERNAL_ERROR.getMessage(),
                path(request));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private String path(WebRequest request) {
        String description = request.getDescription(false);
        return description.startsWith("uri=") ? description.substring(4) : description;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, WebRequest request) {
        log.warn("Login failed - bad credentials");
        ErrorResponse body = ErrorResponse.of(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
            "Kullanıcı adı veya şifre hatalı", path(request));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(DisabledException ex, WebRequest request) {
        log.warn("Login failed - account disabled");
        ErrorResponse body = ErrorResponse.of(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED",
            "Hesabınız devre dışı bırakılmış", path(request));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErrorResponse> handleLocked(LockedException ex, WebRequest request) {
        log.warn("Login failed - account locked");
        ErrorResponse body = ErrorResponse.of(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED",
            "Hesabınız güvenlik sebebiyle kilitlenmiş", path(request));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

}
