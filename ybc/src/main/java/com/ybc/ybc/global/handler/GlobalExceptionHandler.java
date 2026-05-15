package com.ybc.ybc.global.handler;

import com.ybc.ybc.global.exception.ExceptionResponseFactory;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.global.exception.dto.ExceptionResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Optional;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(YbcException.class)
    public ResponseEntity<ExceptionResponse> handleYbcException(YbcException e, HttpServletRequest request) {
        log.warn("Handled YbcException. method={}, uri={}, code={}",
                request.getMethod(),
                request.getRequestURI(),
                e.getExceptionCodeName(),
                e);
        ExceptionResponse response = ExceptionResponseFactory.from(e, request.getRequestURI());
        return ResponseEntity.status(e.getHttpStatusCode())
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ExceptionResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e,
                                                                                   HttpServletRequest request) {
        String errorMessage = "유효하지 않은 입력입니다.";
        FieldError fieldError = e.getFieldError();
        if (fieldError != null) {
            errorMessage = fieldError.getDefaultMessage();
        }

        ExceptionResponse response = ExceptionResponseFactory.badRequest(errorMessage, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ExceptionResponse> handleConstraintViolationException(ConstraintViolationException e,
                                                                                HttpServletRequest request) {
        String errorMessage = Optional.ofNullable(e.getConstraintViolations())
                .flatMap(violations -> violations.stream()
                        .findFirst()
                        .map(ConstraintViolation::getMessage))
                .orElse("유효하지 않은 입력입니다.");

        ExceptionResponse response = ExceptionResponseFactory.badRequest(errorMessage, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ExceptionResponse> handleBadRequestException(Exception exception,
                                                                       HttpServletRequest request) {
        ExceptionResponse response = ExceptionResponseFactory.badRequest("유효하지 않은 입력입니다.", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ExceptionResponse> handleRuntimeException(RuntimeException runtimeException,
                                                                    HttpServletRequest request) {
        log.error("Unhandled runtime exception. method={}, uri={}",
                request.getMethod(),
                request.getRequestURI(),
                runtimeException);
        ExceptionResponse response = ExceptionResponseFactory.internalServerError(request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}
