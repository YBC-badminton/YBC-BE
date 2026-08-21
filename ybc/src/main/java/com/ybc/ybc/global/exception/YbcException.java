package com.ybc.ybc.global.exception;

import org.springframework.http.HttpStatusCode;

public class YbcException extends RuntimeException {
    private final ExceptionCode exceptionCode;

    public YbcException(ExceptionCode exceptionCode) {
        this.exceptionCode = exceptionCode;
    }

    public YbcException(ExceptionCode exceptionCode, Throwable cause) {
        super(cause);
        this.exceptionCode = exceptionCode;
    }

    @Override
    public String getMessage() {
        return exceptionCode.getMessage();
    }

    public HttpStatusCode getHttpStatusCode() {
        return exceptionCode.getHttpStatus();
    }

    public String getExceptionCodeName(){
        return exceptionCode.getClientExceptionCode().name();
    }
}
