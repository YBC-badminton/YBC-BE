package com.ybc.ybc.auth.controller;

import com.ybc.ybc.auth.service.SwaggerLoginService;
import com.ybc.ybc.global.exception.YbcException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@Controller
@RequiredArgsConstructor
public class SwaggerLoginController {
    private final SwaggerLoginService swaggerLoginService;

    @GetMapping(value = "/swagger-login", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getLoginPage(@RequestParam(required = false) String errorCode) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(buildLoginPage(resolveErrorMessage(errorCode)));
    }

    @PostMapping(value = "/swagger-login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> login(@RequestParam(required = false) String accessToken,
                                      HttpServletRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(
                            HttpHeaders.SET_COOKIE,
                            swaggerLoginService.createSwaggerAccessCookie(accessToken, request.isSecure()).toString()
                    )
                    .location(URI.create("/swagger-ui/index.html"))
                    .build();
        } catch (YbcException exception) {
            return redirectToLogin(exception.getExceptionCodeName());
        }
    }

    @GetMapping("/swagger-logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(
                        HttpHeaders.SET_COOKIE,
                        swaggerLoginService.clearSwaggerAccessCookie(request.isSecure()).toString()
                )
                .location(URI.create("/swagger-login"))
                .build();
    }

    private ResponseEntity<Void> redirectToLogin(String errorCode) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/swagger-login?errorCode=" + errorCode))
                .build();
    }

    private String resolveErrorMessage(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return null;
        }
        return switch (errorCode) {
            case "INVALID_JWT_TOKEN" -> "유효한 access token을 입력해 주세요.";
            case "UNAUTHORIZED_USER" -> "Swagger 문서 접근 권한이 없습니다.";
            default -> "Swagger 로그인에 실패했습니다.";
        };
    }

    private String buildLoginPage(String errorMessage) {
        String errorSection = errorMessage == null
                ? ""
                : """
                <p class="error">%s</p>
                """.formatted(errorMessage);

        return """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>YBC Swagger Login</title>
                    <style>
                        body {
                            margin: 0;
                            min-height: 100vh;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            background: linear-gradient(135deg, #f7f4ea 0%%, #e6efe4 100%%);
                            color: #1f2937;
                            font-family: "Helvetica Neue", Helvetica, Arial, sans-serif;
                        }
                        .card {
                            width: min(420px, calc(100%% - 32px));
                            padding: 32px 28px;
                            border-radius: 20px;
                            background: rgba(255, 255, 255, 0.92);
                            box-shadow: 0 20px 45px rgba(31, 41, 55, 0.12);
                        }
                        h1 {
                            margin: 0 0 12px;
                            font-size: 28px;
                        }
                        p {
                            margin: 0 0 16px;
                            line-height: 1.6;
                        }
                        .hint {
                            color: #4b5563;
                            font-size: 14px;
                        }
                        .error {
                            margin-bottom: 16px;
                            padding: 12px 14px;
                            border-radius: 12px;
                            background: #fef2f2;
                            color: #b91c1c;
                            font-size: 14px;
                        }
                        label {
                            display: block;
                            margin-bottom: 8px;
                            font-weight: 600;
                        }
                        input {
                            width: 100%%;
                            box-sizing: border-box;
                            padding: 14px 16px;
                            border: 1px solid #d1d5db;
                            border-radius: 12px;
                            font-size: 14px;
                        }
                        button, .link-button {
                            width: 100%%;
                            margin-top: 12px;
                            padding: 14px 16px;
                            border: 0;
                            border-radius: 12px;
                            background: #166534;
                            color: #ffffff;
                            font-size: 15px;
                            font-weight: 600;
                            cursor: pointer;
                            text-decoration: none;
                            display: inline-flex;
                            align-items: center;
                            justify-content: center;
                            box-sizing: border-box;
                        }
                        .link-button {
                            background: #1f2937;
                        }
                        code {
                            padding: 2px 6px;
                            border-radius: 6px;
                            background: #f3f4f6;
                        }
                    </style>
                </head>
                <body>
                    <main class="card">
                        <h1>Swagger 로그인</h1>
                        <p>운영진 또는 허용된 계정만 Swagger 문서에 접근할 수 있습니다.</p>
                        %s
                        <form method="post" action="/swagger-login">
                            <label for="accessToken">Access Token</label>
                            <input id="accessToken" name="accessToken" type="text" placeholder="Bearer ... 또는 JWT 토큰 문자열" autocomplete="off" required>
                            <button type="submit">Swagger 열기</button>
                        </form>
                        <a class="link-button" href="/swagger-ui/index.html">바로 Swagger 접속</a>
                        <p class="hint">
                            로그인에 성공하면 브라우저에 Swagger 전용 쿠키가 저장됩니다.<br>
                            토큰이 만료되면 다시 로그인해 주세요. <code>/swagger-logout</code> 으로 쿠키를 지울 수 있습니다.
                        </p>
                    </main>
                </body>
                </html>
                """.formatted(errorSection);
    }
}
