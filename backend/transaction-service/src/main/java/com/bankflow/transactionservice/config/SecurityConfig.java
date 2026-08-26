package com.bankflow.transactionservice.config;

import com.bankflow.transactionservice.security.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter) {

        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(
                                (request, response, authException) -> {

                                    response.setStatus(
                                            HttpServletResponse.SC_UNAUTHORIZED
                                    );

                                    response.setContentType(
                                            "application/json"
                                    );

                                    response.getWriter().write(
                                            """
                                            {
                                                "status": 401,
                                                "error": "UNAUTHORIZED",
                                                "message": "Authentication required"
                                            }
                                            """
                                    );
                                }
                        )
                )

                .authorizeHttpRequests(auth ->
                        auth
                                /*
                                 * When a handler throws, the container re-dispatches to
                                 * /error. That dispatch carries no SecurityContext, so
                                 * without this the filter chain treats it as anonymous and
                                 * returns 401 -- silently masking every 500 as an auth
                                 * failure.
                                 */
                                .dispatcherTypeMatchers(DispatcherType.ERROR)
                                .permitAll()

                                .requestMatchers(
                                        "/actuator/health",
                                        "/actuator/health/**"
                                ).permitAll()

                                .anyRequest()
                                .authenticated()
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
