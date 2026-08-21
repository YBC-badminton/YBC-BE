package com.ybc.ybc.auth.service;

import com.ybc.ybc.auth.config.JwtProperties;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private static final String REFRESH_TOKEN_KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtProperties jwtProperties;

    // 회원별 리프레시 토큰을 Redis에 저장
    public void save(Long memberId, String refreshToken) {
        execute(() -> stringRedisTemplate.opsForValue().set(
                key(memberId),
                refreshToken,
                Duration.ofSeconds(jwtProperties.refreshTokenExpirationSeconds())
        ));
    }

    // 요청된 리프레시 토큰이 Redis에 저장된 값과 일치하는지 확인
    public void validate(Long memberId, String refreshToken) {
        String savedRefreshToken = execute(() -> stringRedisTemplate.opsForValue().get(key(memberId)));
        if (savedRefreshToken == null || !savedRefreshToken.equals(refreshToken)) {
            throw new YbcException(ExceptionCode.REFRESH_TOKEN_NOT_FOUND);
        }
    }

    // 회원의 리프레시 토큰을 Redis에서 삭제
    public void delete(Long memberId) {
        execute(() -> stringRedisTemplate.delete(key(memberId)));
    }

    // 회원별 리프레시 토큰 저장 키 생성
    private String key(Long memberId) {
        return REFRESH_TOKEN_KEY_PREFIX + memberId;
    }

    private void execute(Runnable action) {
        try {
            action.run();
        } catch (DataAccessException exception) {
            throw new YbcException(ExceptionCode.REFRESH_TOKEN_STORE_UNAVAILABLE, exception);
        }
    }

    private <T> T execute(Supplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessException exception) {
            throw new YbcException(ExceptionCode.REFRESH_TOKEN_STORE_UNAVAILABLE, exception);
        }
    }
}
