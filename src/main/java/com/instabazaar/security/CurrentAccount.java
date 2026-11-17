package com.instabazaar.security;

import com.instabazaar.entity.AppUser;
import com.instabazaar.exception.DomainException;
import com.instabazaar.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentAccount {
    private final UserRepository users;
    public CurrentAccount(UserRepository users) { this.users = users; }

    public AppUser optional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) return null;
        return users.findById(principal.id()).filter(user -> user.getActive()).orElse(null);
    }

    public AppUser require() {
        AppUser user = optional();
        if (user == null) throw DomainException.forbidden();
        return user;
    }
}
