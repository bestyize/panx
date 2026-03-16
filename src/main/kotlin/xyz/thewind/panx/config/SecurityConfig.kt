package xyz.thewind.panx.config

import xyz.thewind.panx.service.PanxUserDetailsService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig(
    private val panxUserDetailsService: PanxUserDetailsService,
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(authenticationConfiguration: AuthenticationConfiguration): AuthenticationManager =
        authenticationConfiguration.authenticationManager

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .userDetailsService(panxUserDetailsService)
            .authorizeHttpRequests {
                it.requestMatchers("/css/**", "/login", "/share/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/files/*/preview").permitAll()
                    .requestMatchers(HttpMethod.HEAD, "/api/files/*/preview").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/shares/*").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/shares/*/verify").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/shares/*/download").permitAll()
                    .requestMatchers(HttpMethod.HEAD, "/api/shares/*/download").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/shares/*/preview").permitAll()
                    .requestMatchers(HttpMethod.HEAD, "/api/shares/*/preview").permitAll()
                    .anyRequest().authenticated()
            }
            .formLogin {
                it.loginPage("/login")
                    .defaultSuccessUrl("/app/files", true)
                    .permitAll()
            }
            .logout {
                it.logoutUrl("/logout")
                    .logoutSuccessUrl("/login?logout")
            }
            .httpBasic(Customizer.withDefaults())

        return http.build()
    }
}
