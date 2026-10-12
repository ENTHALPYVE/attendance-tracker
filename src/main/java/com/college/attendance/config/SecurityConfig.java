package com.college.attendance.config;

import com.college.attendance.dto.UserDto;
import com.college.attendance.security.AppUserPrincipal;
import com.college.attendance.security.CsrfCookieFilter;
import com.college.attendance.security.FailedAttemptLimiter;
import com.college.attendance.security.LoginRateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

import java.io.IOException;
import java.util.Map;

/**
 * Security rules.
 * <ul>
 *   <li>Login is session based: POST /api/auth/login with email + password (form encoded).
 *       On success Spring stores the user in the HTTP session (JSESSIONID cookie).</li>
 *   <li>Passwords are stored as BCrypt hashes.</li>
 *   <li>/api/admin/** is for ADMIN only; every other /api/** call needs a logged-in user.
 *       Finer checks (e.g. "is this teacher assigned to this session?") are done in the services.</li>
 *   <li>The HTML/CSS/JS files are public; they contain no data and fetch everything from the API.</li>
 *   <li>CSRF protection: the token is sent to the browser in the XSRF-TOKEN cookie and
 *       api.js sends it back in the X-XSRF-TOKEN header on every POST/PUT/DELETE.</li>
 *   <li>Wrong passwords are limited (LoginRateLimitFilter): 5 per email and IP address,
 *       20 per email, then a 15-minute wait.</li>
 *   <li>Security headers: a Content-Security-Policy that only allows scripts from this site and
 *       the two CDNs (no inline scripts), no framing, no referrer to other sites, no camera /
 *       microphone / location, plus Spring's defaults (nosniff, HSTS over HTTPS, no caching of
 *       responses).</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Where the browser may load things from. Scripts: this site and the two CDNs (with
     * integrity hashes in the pages). Styles allow inline style="" attributes, which the pages
     * build in JavaScript. connect-src lists the CDNs because the service worker fetches them.
     */
    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self' https://unpkg.com https://cdnjs.cloudflare.com",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' https://fonts.gstatic.com",
            "img-src 'self' data: blob:",
            "connect-src 'self' https://unpkg.com https://cdnjs.cloudflare.com https://fonts.googleapis.com https://fonts.gstatic.com",
            "manifest-src 'self'",
            "worker-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    private final ObjectMapper objectMapper;
    private final FailedAttemptLimiter limiter;

    public SecurityConfig(ObjectMapper objectMapper, FailedAttemptLimiter limiter) {
        this.objectMapper = objectMapper;
        this.limiter = limiter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        LoginRateLimitFilter loginLimit = new LoginRateLimitFilter(limiter, objectMapper);
        http
            // The token travels in a cookie the JavaScript can read (HttpOnly = false) and is
            // compared as-is (plain handler), because api.js sends back the raw cookie value.
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            .addFilterBefore(loginLimit, UsernamePasswordAuthenticationFilter.class)
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                        "camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
                .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin")))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/api/auth/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler((request, response, authentication) -> {
                    // Spring replaces the CSRF token at login; send the new one in this response.
                    CsrfToken newToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                    if (newToken != null) {
                        newToken.getToken();
                    }
                    loginLimit.recordSuccess(request);
                    AppUserPrincipal user = (AppUserPrincipal) authentication.getPrincipal();
                    writeJson(response, HttpStatus.OK, UserDto.from(user));
                })
                .failureHandler((request, response, exception) -> {
                    loginLimit.recordFailure(request);
                    writeJson(response, HttpStatus.UNAUTHORIZED,
                        Map.of("message", "Incorrect email or password."));
                }))
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.OK)))
            .exceptionHandling(ex -> ex
                // Return JSON errors instead of redirecting to a login page.
                .authenticationEntryPoint((request, response, e) ->
                    writeJson(response, HttpStatus.UNAUTHORIZED, Map.of("message", "Please log in.")))
                .accessDeniedHandler((request, response, e) ->
                    writeJson(response, HttpStatus.FORBIDDEN,
                        Map.of("message", "You do not have permission to do this."))));

        return http.build();
    }

    private void writeJson(HttpServletResponse response, HttpStatus status, Object body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
