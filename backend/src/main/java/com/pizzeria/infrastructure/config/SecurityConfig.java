package com.pizzeria.infrastructure.config;

import com.pizzeria.infrastructure.adapter.in.web.JwtAuthenticationFilter;
import com.pizzeria.shared.security.JwtService;
import com.pizzeria.domain.port.out.TokenRevocationPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, TokenRevocationPort tokenRevocationPort) throws Exception {
        http
            // CSRF deshabilitado intencionadamente: la API es stateless y usa JWT en la cabecera
            // Authorization, no en cookies. Sin estado de sesión en el servidor no hay superficie
            // de ataque CSRF. Si en el futuro se añaden cookies de sesión, hay que reactivarlo.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**", "/api/v1/products/**", "/api/v1/categories/**", "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .requestMatchers("/api/v1/employee/**").hasAnyRole("ADMIN", "EMPLOYEE")
                .anyRequest().authenticated()
            )
            .addFilterBefore(new JwtAuthenticationFilter(jwtService, tokenRevocationPort), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
