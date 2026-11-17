package com.instabazaar.security;

import com.instabazaar.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

public class ActiveAccountFilter extends OncePerRequestFilter {
    private final UserRepository users;
    public ActiveAccountFilter(UserRepository users) { this.users = users; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            var account = users.findById(principal.id()).filter(user -> user.getActive());
            if (account.isEmpty()) {
                new SecurityContextLogoutHandler().logout(request, response, authentication);
            } else {
                var updated = AccountPrincipal.from(account.get());
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(updated, null, updated.getAuthorities()));
            }
        }
        chain.doFilter(request, response);
    }
}
