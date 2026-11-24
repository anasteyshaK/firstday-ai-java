package com.novatech.firstday;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

public class ApiException extends RuntimeException {
    final int status;
    public ApiException(int status, String message) { super(message); this.status=status; }
    static ApiException missing(String text) { return new ApiException(404,text); }

    @RestControllerAdvice
    public static class Handler {
        @ExceptionHandler(ApiException.class)
        ResponseEntity<?> api(ApiException e) { return ResponseEntity.status(e.status).body(Map.of("detail",e.getMessage())); }
        @ExceptionHandler(DuplicateKeyException.class)
        ResponseEntity<?> duplicate(DuplicateKeyException e) { return ResponseEntity.status(409).body(Map.of("detail","A record with these values already exists")); }
        @ExceptionHandler(DataAccessException.class)
        ResponseEntity<?> database(DataAccessException e) { return ResponseEntity.status(503).body(Map.of("detail","Database unavailable or schema not initialized")); }
        @ExceptionHandler({MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,
                org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,jakarta.validation.ConstraintViolationException.class})
        ResponseEntity<?> validation(Exception e) { return ResponseEntity.unprocessableEntity().body(Map.of("detail","Invalid request fields")); }
    }
}
