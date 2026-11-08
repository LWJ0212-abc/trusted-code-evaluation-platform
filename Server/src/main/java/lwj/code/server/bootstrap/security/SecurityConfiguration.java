package lwj.code.server.bootstrap.security;

import lombok.RequiredArgsConstructor;
import lwj.code.server.api.SecurityErrorWriter;
import lwj.code.server.shared.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final SecurityErrorWriter securityErrorWriter;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity security){
        security
                .authorizeHttpRequests(
                        request->request
                                .requestMatchers(
                                        "/error",
                                        "/oauth2/**",
                                        "/login/**"
                                ).permitAll().anyRequest().authenticated()
                )
                .exceptionHandling(
                        exceptions->exceptions
                                .authenticationEntryPoint(
                                        ((request, response, authException) ->securityErrorWriter.write(
                                                request,
                                                response,
                                                ErrorCode.UNAUTHENTICATED,
                                                "Authentication is required",
                                                false
                                        ))
                                )
                                .accessDeniedHandler(
                                        ((request, response, accessDeniedException) -> {
                                            securityErrorWriter.write(
                                                    request,
                                                    response,
                                                    ErrorCode.ACCESS_DENIED,
                                                    "Access is Denied",
                                                    false
                                            );
                                        })
                                )
                )
                .oauth2Login(Customizer.withDefaults());

        return security.build();
    }
}
