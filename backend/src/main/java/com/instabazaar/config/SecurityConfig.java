package com.instabazaar.config;

import com.instabazaar.account.service.AccountService;
import com.instabazaar.security.ApiTraceFilter;
import com.instabazaar.security.SessionAccountFilter;
import com.instabazaar.security.SpaCsrfTokenRequestHandler;
import com.instabazaar.shared.exception.BusinessException;
import com.instabazaar.shared.web.HttpErrors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
  @Bean
  public CookieCsrfTokenRepository csrfTokenRepository(
      @Value("${server.servlet.session.cookie.secure:true}") boolean secure) {
    var repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    repository.setHeaderName("X-CSRF-TOKEN");
    repository.setCookiePath("/");
    repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(secure));
    return repository;
  }

  @Bean
  public SecurityContextRepository securityContextRepository() {
    return new DelegatingSecurityContextRepository(
        new RequestAttributeSecurityContextRepository(),
        new HttpSessionSecurityContextRepository());
  }

  @Bean
  public SessionAuthenticationStrategy sessionAuthenticationStrategy(
      CookieCsrfTokenRepository csrf) {
    return new CompositeSessionAuthenticationStrategy(
        List.of(new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf)));
  }

  @Bean
  public AuthenticationEntryPoint authenticationEntryPoint(HttpErrors errors) {
    return (request, response, failure) ->
        errors.write(
            new BusinessException("AUTHENTICATION_REQUIRED", 401, "Authentication is required."),
            request,
            response);
  }

  @Bean
  public AccessDeniedHandler accessDeniedHandler(HttpErrors errors) {
    return (request, response, failure) ->
        errors.write(
            failure instanceof CsrfException
                ? new BusinessException("CSRF_INVALID", 403, "A valid CSRF token is required.")
                : new BusinessException("ACCESS_DENIED", 403, "This operation is not permitted."),
            request,
            response);
  }

  @Bean
  public UrlBasedCorsConfigurationSource corsConfigurationSource(
      @Value("${instabazaar.web.allowed-origins:http://localhost:5173}") String origins) {
    var allowed =
        Arrays.stream(origins.split(","))
            .map(String::strip)
            .filter(origin -> !origin.isEmpty())
            .toList();
    if (allowed.isEmpty()) throw new IllegalArgumentException("Configure at least one SPA origin.");
    for (String origin : allowed) {
      URI uri = URI.create(origin);
      if (!List.of("http", "https").contains(uri.getScheme())
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null
          || !uri.getPath().isEmpty()
          || origin.contains("*")) {
        throw new IllegalArgumentException("SPA origins must be exact HTTP(S) origins.");
      }
    }
    var configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(allowed);
    configuration.setAllowCredentials(true);
    configuration.setAllowedMethods(
        List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Accept", "Content-Type", "X-CSRF-TOKEN"));
    configuration.setExposedHeaders(List.of("Location", "X-Trace-ID"));
    configuration.setMaxAge(600L);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      CookieCsrfTokenRepository csrf,
      SecurityContextRepository contexts,
      AuthenticationEntryPoint entryPoint,
      AccessDeniedHandler denied,
      UrlBasedCorsConfigurationSource corsSource,
      AccountService accounts,
      HttpErrors errors)
      throws Exception {
    // Keep this filter inside the security chain; a servlet bean would run it twice.
    var cors = new CorsFilter(corsSource);
    cors.setCorsProcessor(
        new DefaultCorsProcessor() {
          @Override
          public boolean processRequest(
              CorsConfiguration configuration,
              HttpServletRequest request,
              HttpServletResponse response)
              throws IOException {
            boolean accepted = super.processRequest(configuration, request, response);
            if (!accepted && !response.isCommitted())
              errors.write(
                  new BusinessException(
                      "ACCESS_DENIED", 403, "Origin or CORS request is not permitted."),
                  request,
                  response);
            return accepted;
          }

          @Override
          protected void rejectRequest(ServerHttpResponse response) {
            // processRequest writes the API error using the servlet response.
          }
        });
    http.cors(AbstractHttpConfigurer::disable)
        .csrf(
            config ->
                config
                    .csrfTokenRepository(csrf)
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
        .securityContext(
            config -> config.securityContextRepository(contexts).requireExplicitSave(true))
        .sessionManagement(
            config -> config.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .requestCache(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .exceptionHandling(
            config -> config.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied))
        .authorizeHttpRequests(
            routes ->
                routes
                    .requestMatchers("/api/v1/auth/**")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/health",
                        "/api/v1/products/**",
                        "/api/v1/sellers/**",
                        "/api/v1/categories/**",
                        "/api/v1/media/**")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.HEAD,
                        "/api/v1/health",
                        "/api/v1/products/**",
                        "/api/v1/sellers/**",
                        "/api/v1/categories/**",
                        "/api/v1/media/**")
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/me",
                        "/api/v1/me/**",
                        "/api/v1/reports",
                        "/api/v1/reports/**",
                        "/api/v1/reviews/**")
                    .hasAnyRole("BUYER", "SELLER", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/products/*/reviews")
                    .hasAnyRole("BUYER", "SELLER", "ADMIN")
                    .requestMatchers("/api/v1/seller/**")
                    .hasRole("SELLER")
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .denyAll())
        .addFilterBefore(new ApiTraceFilter(), CorsFilter.class)
        .addFilterAt(cors, CorsFilter.class)
        .addFilterBefore(new SessionAccountFilter(accounts, errors), AuthorizationFilter.class);
    return http.build();
  }
}
