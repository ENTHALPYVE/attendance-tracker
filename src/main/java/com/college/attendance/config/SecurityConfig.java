package com.college.attendance.config;

import com.college.attendance.dto.UserDto;
import com.college.attendance.security.AppUserPrincipal;
import com.college.attendance.security.CsrfCookieFilter;
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
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

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
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // The token travels in a cookie the JavaScript can read (HttpOnly = false) and is
            // compared as-is (plain handler), because api.js sends back the raw cookie value.
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
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
                    AppUserPrincipal user = (AppUserPrincipal) authentication.getPrincipal();
                    writeJson(response, HttpStatus.OK, UserDto.from(user));
                })
                .failureHandler((request, response, exception) ->
                    writeJson(response, HttpStatus.UNAUTHORIZED,
                        Map.of("message", "Incorrect email or password."))))
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
