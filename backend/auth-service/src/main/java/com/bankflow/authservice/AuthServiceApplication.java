package com.bankflow.authservice;

import com.bankflow.common.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/*
 * The shared handler lives outside this package, so component scanning never
 * finds it and it has to be imported, as the other services do. Without it a
 * taken username went unhandled, fell through to the error page — which is
 * itself behind security — and reached the person registering as
 * "Authentication required".
 */
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
