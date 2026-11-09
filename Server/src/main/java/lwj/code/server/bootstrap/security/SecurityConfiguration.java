package lwj.code.server.bootstrap.security;


import lombok.RequiredArgsConstructor;
import lwj.code.server.api.SecurityErrorWriter;
import lwj.code.server.shared.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final SecurityErrorWriter securityErrorWriter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity security) {
        security
                .authorizeHttpRequests(
                        request -> request
                                .requestMatchers(
                                        "/error",
                                        "/oauth2/**",
                                        "/login/**",
                                        "/api/v1/auth/callback"
                                ).permitAll()
                                .anyRequest().authenticated()
                )
                .exceptionHandling(
                        exceptions -> exceptions
                                .authenticationEntryPoint(
                                        (request, response, exception) -> securityErrorWriter
                                                .write(
                                                        request,
                                                        response,
                                                        ErrorCode.UNAUTHENTICATED,
                                                        "Authentication is required",
                                                        false
                                                )
                                )
                                .accessDeniedHandler(
                                        ((request, response, exception) -> securityErrorWriter
                                                .write(
                                                        request,
                                                        response,
                                                        ErrorCode.ACCESS_DENIED,
                                                        "Access is denied",
                                                        false
                                                ))
                                )

                )
                .oauth2Login(oauth2 ->
                        oauth2.redirectionEndpoint(
                                endpoint -> endpoint.baseUri("/api/v1/auth/callback")
                        )
                );
        return security.build();
    }

}