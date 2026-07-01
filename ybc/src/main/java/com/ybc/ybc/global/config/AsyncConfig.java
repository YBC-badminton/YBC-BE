package com.ybc.ybc.global.config;

import com.ybc.ybc.global.monitoring.service.ErrorAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import java.lang.reflect.Method;
import java.util.Arrays;

@Configuration
@RequiredArgsConstructor
public class AsyncConfig implements AsyncConfigurer {
    private final ErrorAlertService errorAlertService;

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable exception, Method method, Object... params) -> errorAlertService.sendAsyncExceptionAlert(
                method.getDeclaringClass().getSimpleName(),
                method.getName(),
                Arrays.toString(params),
                exception
        );
    }
}
