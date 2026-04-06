package com.ybc.ybc.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ExceptionCode {
    // 전체
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ClientExceptionCode.INTERNAL_SERVER_ERROR, "예상치 못한 서버에러가 발생했습니다."),
    ILLEGAL_ARGUMENT(HttpStatus.BAD_REQUEST, ClientExceptionCode.ILLEGAL_ARGUMENT, "필수 파라미터 누락"),
    UNAUTHORIZED_USER(HttpStatus.UNAUTHORIZED, ClientExceptionCode.UNAUTHORIZED_USER, "접근 권한이 없는 회원입니다."),
    KAKAO_AUTH_FAILED(HttpStatus.BAD_GATEWAY, ClientExceptionCode.KAKAO_AUTH_FAILED, "카카오 로그인 처리 중 문제가 발생했습니다."),
    INVALID_JWT_TOKEN(HttpStatus.UNAUTHORIZED, ClientExceptionCode.INVALID_JWT_TOKEN, "유효하지 않은 JWT 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, ClientExceptionCode.REFRESH_TOKEN_NOT_FOUND, "다시 로그인이 필요합니다."),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, ClientExceptionCode.APPLICATION_NOT_FOUND, "존재하지 않는 지원서입니다."),
    INVALID_APPLICATION_STATUS(HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_APPLICATION_STATUS, "지원자 상태값이 올바르지 않습니다."),
    APPLICATION_NOT_INTERVIEW_TARGET(HttpStatus.BAD_REQUEST, ClientExceptionCode.APPLICATION_NOT_INTERVIEW_TARGET, "면접 관리 대상 지원서가 아닙니다."),
    INVALID_INTERVIEW_STATUS(HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_INTERVIEW_STATUS, "면접 상태값이 올바르지 않습니다."),

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
