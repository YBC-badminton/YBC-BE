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
    GUEST_NOT_FOUND(HttpStatus.NOT_FOUND, ClientExceptionCode.GUEST_NOT_FOUND, "존재하지 않는 게스트입니다."),
    GUEST_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, ClientExceptionCode.GUEST_DELETE_FORBIDDEN, "해당 게스트를 삭제할 권한이 없습니다."),
    INVALID_VOTE_ACTIVITY_TYPE(HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_VOTE_ACTIVITY_TYPE, "투표 활동 유형이 올바르지 않습니다."),
    INVALID_VOTE_PERIOD(HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_VOTE_PERIOD, "투표 시작 시간과 종료 시간을 다시 확인해 주세요."),
    INVALID_VOTE_CAPACITY(HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_VOTE_CAPACITY, "투표 정원은 1명 이상이어야 합니다."),
    DUPLICATE_VOTE_ATTENDANCE(HttpStatus.BAD_REQUEST, ClientExceptionCode.DUPLICATE_VOTE_ATTENDANCE, "같은 운동에는 한 번만 참여할 수 있습니다."),
    INVALID_MATCH_REQUEST(HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_MATCH_REQUEST, "대진 요청이 올바르지 않습니다."),
    MATCH_PARTICIPANT_NOT_FOUND(HttpStatus.BAD_REQUEST, ClientExceptionCode.MATCH_PARTICIPANT_NOT_FOUND, "대진에 포함된 참석자를 찾을 수 없습니다."),
    MATCH_NOT_FOUND(HttpStatus.NOT_FOUND, ClientExceptionCode.MATCH_NOT_FOUND, "존재하지 않는 대진입니다."),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, ClientExceptionCode.APPLICATION_NOT_FOUND, "존재하지 않는 지원서입니다."),
    INVALID_APPLICATION_STATUS(HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_APPLICATION_STATUS, "지원자 상태값이 올바르지 않습니다."),
    APPLICATION_NOT_INTERVIEW_TARGET(HttpStatus.BAD_REQUEST, ClientExceptionCode.APPLICATION_NOT_INTERVIEW_TARGET, "면접 관리 대상 지원서가 아닙니다."),
    INVALID_INTERVIEW_STATUS(HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_INTERVIEW_STATUS, "면접 상태값이 올바르지 않습니다."),
    RECRUITMENT_NOT_FOUND(HttpStatus.NOT_FOUND, ClientExceptionCode.RECRUITMENT_NOT_FOUND, "존재하지 않는 모집 기수입니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, ClientExceptionCode.MEMBER_NOT_FOUND, "존재하지 않는 부원입니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, ClientExceptionCode.REVIEW_NOT_FOUND, "존재하지 않는 장비 후기입니다."),
    REVIEW_UPDATE_FORBIDDEN(HttpStatus.FORBIDDEN, ClientExceptionCode.REVIEW_UPDATE_FORBIDDEN, "해당 장비 후기를 수정할 권한이 없습니다."),
    REVIEW_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, ClientExceptionCode.REVIEW_DELETE_FORBIDDEN, "해당 장비 후기를 삭제할 권한이 없습니다."),
    INVALID_RECRUITMENT_SCHEDULE(HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_RECRUITMENT_SCHEDULE, "모집 일정 순서가 올바르지 않습니다."),
    INVALID_FRONTEND_REDIRECT_URI(HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_FRONTEND_REDIRECT_URI, "허용되지 않은 프론트엔드 리다이렉트 주소입니다."),
    INVALID_EQUIPMENT_REVIEW_RATING(HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_EQUIPMENT_REVIEW_RATING, "장비 후기 평점은 1점부터 5점까지만 가능합니다."),
    INVALID_EQUIPMENT_USAGE_MONTH(HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_EQUIPMENT_USAGE_MONTH, "장비 사용 기간은 1개월 이상이어야 합니다."),
    INVALID_REVIEW_SORT(HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_REVIEW_SORT, "후기 정렬 조건은 asc 또는 desc만 가능합니다.");

    private final HttpStatus httpStatus;
    private final ClientExceptionCode clientExceptionCode;
    private final String message;

    ExceptionCode(HttpStatus httpStatus, ClientExceptionCode clientExceptionCode, String message) {
        this.httpStatus = httpStatus;
        this.clientExceptionCode = clientExceptionCode;
        this.message = message;
    }
}
