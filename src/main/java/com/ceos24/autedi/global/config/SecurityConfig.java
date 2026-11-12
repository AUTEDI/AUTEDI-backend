package com.ceos24.autedi.global.config;

import com.ceos24.autedi.global.security.CustomAccessDeniedHandler;
import com.ceos24.autedi.global.security.CustomAuthenticationEntryPoint;
import com.ceos24.autedi.global.security.JwtAuthenticationFilter;
import com.ceos24.autedi.global.security.QrRateLimitFilter;
import jakarta.servlet.DispatcherType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    @Value("${app.qr.rate-limit.enabled:true}")
    private boolean qrRateLimitEnabled;

    @Value("${app.qr.rate-limit.scan-per-minute:10}")
    private int scanPerMinute;

    @Value("${app.qr.rate-limit.token-per-minute:10}")
    private int tokenPerMinute;

    @Value("${app.qr.rate-limit.status-per-minute:90}")
    private int statusPerMinute;

    @Value("${app.qr.rate-limit.client-ip-header:}")
    private String clientIpHeader;

    @Value("${swagger.user:autedi}")
    private String swaggerUser;

    @Value("${swagger.password:autedi2026!}")
    private String swaggerPassword;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "https://*.vercel.app",
                "https://*.duckdns.org",
                "http://*.duckdns.org",
                "https://autedi-mvp.duckdns.org",
                "https://*.autedi.com",
                "https://autedi.com",
                "https://*.railway.app",
                "https://*.up.railway.app"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of(HttpHeaders.ETAG, HttpHeaders.AUTHORIZATION, HttpHeaders.LOCATION));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public InMemoryUserDetailsManager swaggerUserDetailsManager(PasswordEncoder passwordEncoder) {
        UserDetails user = User.builder()
                .username(swaggerUser)
                .password(passwordEncoder.encode(swaggerPassword))
                .roles("SWAGGER_ADMIN")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain swaggerSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(
                        "/autedi-docs/**",
                        "/autedi-docs",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/v3/api-docs")
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 서블릿 필터로 중복 등록되지 않도록 빈이 아니라 체인에서만 생성한다.
        QrRateLimitFilter qrRateLimitFilter = new QrRateLimitFilter(
                qrRateLimitEnabled, scanPerMinute, tokenPerMinute, statusPerMinute, clientIpHeader);

        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                        .accessDeniedHandler(customAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // SSE 등 비동기 응답은 연결 종료 시 ASYNC 디스패치로 한 번 더 필터 체인을 지난다.
                        // 이 시점에는 JWT 필터가 실행되지 않아(OncePerRequestFilter 기본 동작) 인증 정보가 없으므로
                        // 인가 검사를 건너뛴다. 최초 요청(REQUEST 디스패치)의 인증은 그대로 검사된다.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/qr-sessions/scan",
                                "/api/v1/auth/qr-sessions/status",
                                "/api/v1/auth/qr-sessions/token")
                        .permitAll()
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/signup",
                                "/api/v1/auth/refresh",
                                "/api/v1/invitations/validate",
                                "/files/**",
                                "/error")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(qrRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
