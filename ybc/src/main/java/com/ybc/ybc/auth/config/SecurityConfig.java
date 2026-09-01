package com.ybc.ybc.auth.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.ybc.ybc.auth.domain.AuthTokenType;
import com.ybc.ybc.auth.domain.MemberPrincipal;
import com.ybc.ybc.auth.service.SwaggerAccessService;
import com.ybc.ybc.auth.service.SwaggerLoginService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import com.ybc.ybc.global.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private static final String[] PUBLIC_AUTH_ENDPOINTS = {
            "/",
            "/api/v1/auth/kakao/login-url",
            "/api/v1/auth/kakao/login",
            "/api/v1/auth/kakao/callback",
            "/api/v1/auth/refresh",
            "/faqs",
            "/activities",
            "/privacy-policy",
            "/recruitments/message",
            "/recruitments/interview-times",
            "/votes/recent",
            "/error"
    };
    private static final String[] PUBLIC_ACTUATOR_ENDPOINTS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus"
    };
    private static final String[] PUBLIC_MONITORING_TEST_ENDPOINTS = {
            "/internal/monitoring/test-alert"
    };
    private static final String[] PUBLIC_SWAGGER_LOGIN_ENDPOINTS = {
            "/swagger-login",
            "/swagger-logout"
    };
    private static final String[] SWAGGER_ENDPOINTS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**"
    };
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final JwtProperties jwtProperties;
    private final SwaggerAccessService swaggerAccessService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   Converter<Jwt, UsernamePasswordAuthenticationToken> jwtAuthenticationConverter,
                                                   RequestMatcher bearerTokenIgnoredRequestMatcher,
                                                   BearerTokenResolver publicEndpointAwareBearerTokenResolver,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        AuthenticationEntryPoint swaggerAwareAuthenticationEntryPoint = swaggerAwareAuthenticationEntryPoint();

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(swaggerAwareAuthenticationEntryPoint))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(swaggerAwareAuthenticationEntryPoint)
                        .bearerTokenResolver(publicEndpointAwareBearerTokenResolver)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(bearerTokenIgnoredRequestMatcher).permitAll()
                        .requestMatchers(PUBLIC_ACTUATOR_ENDPOINTS).permitAll()
                        .requestMatchers(PUBLIC_MONITORING_TEST_ENDPOINTS).permitAll()
                        .requestMatchers(PUBLIC_SWAGGER_LOGIN_ENDPOINTS).permitAll()
                        .requestMatchers(SWAGGER_ENDPOINTS).access((authentication, context) ->
                                new AuthorizationDecision(swaggerAccessService.canAccess(authentication.get())))
                        .requestMatchers(PUBLIC_AUTH_ENDPOINTS).permitAll()
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    @Bean
    public SecretKey jwtSecretKey() {
        byte[] secret = Base64.getDecoder().decode(jwtProperties.secretKey());
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public RequestMatcher bearerTokenIgnoredRequestMatcher() {
        return new OrRequestMatcher(
                request -> HttpMethod.OPTIONS.matches(request.getMethod()),
                request -> HttpMethod.POST.matches(request.getMethod())
                        && request.getRequestURI().startsWith("/applications"),
                request -> HttpMethod.GET.matches(request.getMethod())
                        && "/applications/availability".equals(request.getRequestURI())
        );
    }

    @Bean
    public BearerTokenResolver publicEndpointAwareBearerTokenResolver(RequestMatcher bearerTokenIgnoredRequestMatcher) {
        DefaultBearerTokenResolver defaultBearerTokenResolver = new DefaultBearerTokenResolver();
        RequestMatcher swaggerRequestMatcher = swaggerRequestMatcher();

        return request -> {
            if (bearerTokenIgnoredRequestMatcher.matches(request)) {
                return null;
            }

            String bearerToken = defaultBearerTokenResolver.resolve(request);
            if (StringUtils.hasText(bearerToken)) {
                return bearerToken;
            }

            if (swaggerRequestMatcher.matches(request)) {
                return resolveSwaggerCookieToken(request);
            }
            return null;
        };
    }

    @Bean
    public Converter<Jwt, UsernamePasswordAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> {
            if (!AuthTokenType.ACCESS.value().equals(jwt.getClaimAsString("tokenType"))) {
                throw new BadCredentialsException("Access token is required");
            }

            MemberPrincipal principal = MemberPrincipal.fromJwt(jwt);
            return UsernamePasswordAuthenticationToken.authenticated(
                    principal,
                    jwt.getTokenValue(),
                    principal.getAuthorities()
            );
        };
    }

    private RequestMatcher swaggerUiRequestMatcher() {
        return request -> {
            String requestUri = request.getRequestURI();
            return "/swagger-ui.html".equals(requestUri) || requestUri.startsWith("/swagger-ui/");
        };
    }

    private RequestMatcher swaggerRequestMatcher() {
        return new OrRequestMatcher(
                swaggerUiRequestMatcher(),
                request -> {
                    String requestUri = request.getRequestURI();
                    return "/v3/api-docs".equals(requestUri) || requestUri.startsWith("/v3/api-docs/");
                }
        );
    }

    private String resolveSwaggerCookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (SwaggerLoginService.SWAGGER_ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName())
                    && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private AuthenticationEntryPoint swaggerAwareAuthenticationEntryPoint() {
        RequestMatcher swaggerUiRequestMatcher = swaggerUiRequestMatcher();
        LoginUrlAuthenticationEntryPoint swaggerLoginEntryPoint =
                new LoginUrlAuthenticationEntryPoint("/swagger-login");

        return (request, response, authException) -> {
            if (swaggerUiRequestMatcher.matches(request)) {
                swaggerLoginEntryPoint.commence(request, response, authException);
                return;
            }
            restAuthenticationEntryPoint.commence(request, response, authException);
        };
    }
}
