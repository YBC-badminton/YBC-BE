package com.ybc.ybc.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ExceptionCode {
    // 전체
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ClientExceptionCode.INTERNAL_SERVER_ERROR, "예상치 못한 서버에러가 발생했습니다."),
    ILLEGAL_ARGUMENT(HttpStatus.BAD_REQUEST, ClientExceptionCode.ILLEGAL_ARGUMENT, "필수 파라미터 누락"),
    UNAUTHORIZED_USER(HttpStatus.UNAUTHORIZED, ClientExceptionCode.UNAUTHORIZED_USER, "접근 권한이 없는 회원입니다."),

    // 모임
    SESSION_FULL(HttpStatus.BAD_REQUEST, ClientExceptionCode.SESSION_FULL, "해당 모임의 정원이 모두 찼습니다.");

    private final HttpStatus httpStatus;
    private final ClientExceptionCode clientExceptionCode;
    private final String message;

    ExceptionCode(HttpStatus httpStatus, ClientExceptionCode clientExceptionCode, String message) {
        this.httpStatus = httpStatus;
        this.clientExceptionCode = clientExceptionCode;
        this.message = message;
    }
}
