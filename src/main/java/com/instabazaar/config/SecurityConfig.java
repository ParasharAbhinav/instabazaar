package com.instabazaar.config;

import com.instabazaar.repository.UserRepository;
import com.instabazaar.security.AccountPrincipal;
import com.instabazaar.security.ActiveAccountFilter;
import com.instabazaar.util.Text;
import jakarta.servlet.DispatcherType;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmail(Text.clean(email).toLowerCase(Locale.ROOT))
                .map(AccountPrincipal::from).orElseThrow(() -> new UsernameNotFoundException("Invalid account."));
    }

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository users) throws Exception {
        http.authorizeHttpRequests(routes -> routes
                .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                .requestMatchers("/login", "/register", "/error", "/access-denied").permitAll()
                .requestMatchers(HttpMethod.GET, "/", "/products", "/products/*", "/sellers/**", "/categories/*", "/media/*", "/css/**", "/js/**", "/images/**", "/vendor/**", "/favicon.ico").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/media/*", "/css/**", "/js/**", "/images/**", "/vendor/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/seller/**").hasRole("SELLER")
                .requestMatchers("/wishlist", "/wishlist/**", "/profile", "/account").authenticated()
                .requestMatchers(HttpMethod.POST, "/products/*/reviews", "/reviews/*/delete").authenticated()
                .anyRequest().denyAll())
                .formLogin(login -> login.loginPage("/login").usernameParameter("email")
                        .defaultSuccessUrl("/account", true).failureUrl("/login?error"))
                .logout(logout -> logout.logoutSuccessUrl("/?signedOut").invalidateHttpSession(true).deleteCookies("JSESSIONID"))
                .exceptionHandling(errors -> errors.accessDeniedPage("/access-denied"))
                .headers(headers -> headers.contentSecurityPolicy(policy -> policy.policyDirectives(
                        "default-src 'self'; img-src 'self'; style-src 'self'; script-src 'self'; font-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'")))
                .addFilterBefore(new ActiveAccountFilter(users), AuthorizationFilter.class);
        return http.build();
    }
}
