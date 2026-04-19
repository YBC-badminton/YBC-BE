package com.ybc.ybc.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ExceptionCode {
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ClientExceptionCode.INTERNAL_SERVER_ERROR, "예상치 못한 서버에러가 발생했습니다."),
    ILLEGAL_ARGUMENT(HttpStatus.BAD_REQUEST, ClientExceptionCode.ILLEGAL_ARGUMENT, "필수 파라미터 누락"),
    UNAUTHORIZED_USER(HttpStatus.UNAUTHORIZED, ClientExceptionCode.UNAUTHORIZED_USER, "접근 권한이 없는 회원입니다."),
    KAKAO_AUTH_FAILED(HttpStatus.BAD_GATEWAY, ClientExceptionCode.KAKAO_AUTH_FAILED, "카카오 로그인 처리 중 문제가 발생했습니다."),
    INVALID_JWT_TOKEN(HttpStatus.UNAUTHORIZED, ClientExceptionCode.INVALID_JWT_TOKEN, "유효하지 않은 JWT 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, ClientExceptionCode.REFRESH_TOKEN_NOT_FOUND, "다시 로그인이 필요합니다."),
    SESSION_FULL(HttpStatus.BAD_REQUEST, ClientExceptionCode.SESSION_FULL, "해당 모임의 정원이 모두 찼습니다."),
    VOTE_CLOSED(HttpStatus.BAD_REQUEST, ClientExceptionCode.VOTE_CLOSED, "마감된 투표에는 참여할 수 없습니다."),
    VOTE_NOT_FOUND(HttpStatus.NOT_FOUND, ClientExceptionCode.VOTE_NOT_FOUND, "존재하지 않는 투표입니다."),
    INVALID_VOTE_ACTIVITY_TYPE(HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_VOTE_ACTIVITY_TYPE, "투표 활동 유형이 올바르지 않습니다."),
    INVALID_VOTE_PERIOD(HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_VOTE_PERIOD, "투표 시작 시간과 종료 시간을 다시 확인해 주세요."),
    INVALID_VOTE_CAPACITY(HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_VOTE_CAPACITY, "투표 정원은 1명 이상이어야 합니다."),
    INVALID_MATCH_REQUEST(HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_MATCH_REQUEST, "대진 요청이 올바르지 않습니다."),
    MATCH_PARTICIPANT_NOT_FOUND(HttpStatus.BAD_REQUEST, ClientExceptionCode.MATCH_PARTICIPANT_NOT_FOUND, "대진에 포함된 참석자를 찾을 수 없습니다."),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, ClientExceptionCode.APPLICATION_NOT_FOUND, "존재하지 않는 지원서입니다."),
    INVALID_APPLICATION_STATUS(HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_APPLICATION_STATUS, "지원자 상태값이 올바르지 않습니다."),
    APPLICATION_NOT_INTERVIEW_TARGET(HttpStatus.BAD_REQUEST, ClientExceptionCode.APPLICATION_NOT_INTERVIEW_TARGET, "면접 관리 대상 지원서가 아닙니다."),
    INVALID_INTERVIEW_STATUS(HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_INTERVIEW_STATUS, "면접 상태값이 올바르지 않습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, ClientExceptionCode.MEMBER_NOT_FOUND, "존재하지 않는 부원입니다."),
    INVALID_MEMBER_COURT(HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_MEMBER_COURT, "코트 번호는 1부터 4까지만 가능합니다.");

    private final HttpStatus httpStatus;
    private final ClientExceptionCode clientExceptionCode;
    private final String message;

    ExceptionCode(HttpStatus httpStatus, ClientExceptionCode clientExceptionCode, String message) {
        this.httpStatus = httpStatus;
        this.clientExceptionCode = clientExceptionCode;
        this.message = message;
    }
}
