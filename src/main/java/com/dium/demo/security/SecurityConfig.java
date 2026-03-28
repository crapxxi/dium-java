package com.dium.demo.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                                .requestMatchers("/error").permitAll()

                                .requestMatchers("/api/v1/auth/**").permitAll()
                                .requestMatchers("/api/v1/files/**").permitAll()

                                .requestMatchers("/api/v1/venues/**", "/api/v1/venues").permitAll()
                                .requestMatchers("/api/v1/products/venue/**").permitAll()

//                              .requestMatchers("/v3/api-docs/**").permitAll()
//                              .requestMatchers("/swagger-ui/**").permitAll()
//                              .requestMatchers("/swagger-ui.html").permitAll()
//                              .requestMatchers("/swagger-resources/**").permitAll()
//                              .requestMatchers("/webjars/**").permitAll()
//                              .requestMatchers("/v3/api-docs").permitAll()


                                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")


                                .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        String frontendUrl = System.getenv("FRONTEND_URL");

        if (frontendUrl != null) {
            configuration.setAllowedOrigins(List.of(frontendUrl, "http://localhost:4200"));
        } else {
            configuration.setAllowedOrigins(List.of(
                    "https://dium.kz",
                    "https://dium.vercel.app",
                    "http://localhost:4200"
            ));
        }

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Cache-Control"));

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public StandardServletMultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }
}