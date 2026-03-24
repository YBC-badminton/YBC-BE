package com.ybc.ybc.global.handler;

import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.global.exception.dto.ExceptionResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.ZonedDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(YbcException.class)
    public ResponseEntity<ExceptionResponse> handleYbcException(YbcException e, HttpServletRequest request) {
        ExceptionResponse response = new ExceptionResponse(
                e.getHttpStatusCode().value(),
                e.getExceptionCodeName(),
                e.getMessage(),
                request.getRequestURI(), ZonedDateTime.now()
        );
        return ResponseEntity.status(e.getHttpStatusCode())
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ExceptionResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e, HttpServletRequest request) {
        String errorMessage = "유효하지 않은 입력입니다.";
        FieldError fieldError = e.getFieldError();
        if(fieldError != null) {
            errorMessage = fieldError.getDefaultMessage();
        }
        ExceptionResponse response = new ExceptionResponse(
                HttpStatus.BAD_REQUEST.value(),
                ExceptionCode.ILLEGAL_ARGUMENT.name(),
                errorMessage,
                request.getRequestURI(), ZonedDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ExceptionResponse> handleRuntimeException(RuntimeException runtimeException,
                                                                    HttpServletRequest request) {
        ExceptionResponse response = new ExceptionResponse(
                500,
                ExceptionCode.INTERNAL_SERVER_ERROR.getClientExceptionCode().name(),
                ExceptionCode.INTERNAL_SERVER_ERROR.getMessage(),
                request.getRequestURI(), ZonedDateTime.now());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}
