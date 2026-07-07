package com.ybc.ybc.auth.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.ybc.ybc.auth.domain.AuthTokenType;
import com.ybc.ybc.auth.domain.MemberPrincipal;
import com.ybc.ybc.global.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
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
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private static final String[] PUBLIC_AUTH_ENDPOINTS = {
            "/api/v1/auth/kakao/login-url",
            "/api/v1/auth/kakao/login",
            "/api/v1/auth/kakao/callback",
            "/api/v1/auth/refresh",
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
    private static final String[] PUBLIC_SWAGGER_ENDPOINTS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**"
    };

    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final JwtProperties jwtProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   Converter<Jwt, UsernamePasswordAuthenticationToken> jwtAuthenticationConverter,
                                                   RequestMatcher bearerTokenIgnoredRequestMatcher,
                                                   BearerTokenResolver publicEndpointAwareBearerTokenResolver,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(restAuthenticationEntryPoint))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .bearerTokenResolver(publicEndpointAwareBearerTokenResolver)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(bearerTokenIgnoredRequestMatcher).permitAll()
                        .requestMatchers(PUBLIC_ACTUATOR_ENDPOINTS).permitAll()
                        .requestMatchers(PUBLIC_MONITORING_TEST_ENDPOINTS).permitAll()
                        .requestMatchers(PUBLIC_SWAGGER_ENDPOINTS).permitAll()
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

        return request -> bearerTokenIgnoredRequestMatcher.matches(request)
                ? null
                : defaultBearerTokenResolver.resolve(request);
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
}
