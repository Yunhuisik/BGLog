package com.pubgsite.bglog.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PlayerNotFoundException.class)
    public ResponseEntity<String> handlePlayerNotFound(PlayerNotFoundException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException e) {
        Map<String, Object> body = Map.of(
                "status", e.getStatusCode().value(),
                "message", e.getReason() == null ? "요청 처리 중 오류가 발생했습니다." : e.getReason()
        );

        return ResponseEntity.status(e.getStatusCode()).body(body);
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoResource() {
        return "Not Found";
    }

    @ExceptionHandler(HttpClientErrorException.TooManyRequests.class)
    public ResponseEntity<Map<String, Object>> handleTooMany(HttpClientErrorException.TooManyRequests e) {
        Map<String, Object> body = Map.of(
                "status", 429,
                "message", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."
        );

        return ResponseEntity.status(429).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception e) {
        e.printStackTrace();

        Map<String, Object> body = Map.of(
                "status", 500,
                "message", "서버 오류가 발생했습니다."
        );

        return ResponseEntity.internalServerError().body(body);
    }
}