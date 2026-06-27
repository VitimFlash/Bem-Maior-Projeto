package game.bmm.config;

import game.bmm.service.UsuarioDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private UsuarioDetailsService usuarioDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/index.html", "/lobby.html",
                                "/game.html", "/resultado.html",
                                "/style.css", "/game.js",
                                "/*.js", "/*.css", "/*.html",
                                "/auth/login", "/auth/cadastro",
                                "/ws-bemmaior", "/ws-bemmaior/**",
                                "/h2-console/**"
                        ).permitAll()
                        .requestMatchers("/auth/login", "/auth/cadastro").permitAll()
                        .requestMatchers("/ws-bemmaior/**", "/ws-bemmaior").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.disable())
                )
                // Permite múltiplas sessões simultâneas
                .sessionManagement(session -> session
                        .maximumSessions(10)          // até 10 sessões por usuário
                        .maxSessionsPreventsLogin(false) // não bloqueia novo login
                );

        return http.build();
    }
}