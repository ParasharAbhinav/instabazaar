package com.instabazaar.service.impl;

import com.instabazaar.dto.Forms;
import com.instabazaar.dto.Views;
import com.instabazaar.entity.AppUser;
import com.instabazaar.entity.SellerProfile;
import com.instabazaar.enums.Role;
import com.instabazaar.exception.DomainException;
import com.instabazaar.repository.SellerRepository;
import com.instabazaar.repository.UserRepository;
import com.instabazaar.security.CurrentAccount;
import com.instabazaar.service.AccountService;
import com.instabazaar.util.Text;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated @Transactional
public class AccountServiceImpl implements AccountService {
    private final UserRepository users;
    private final SellerRepository sellers;
    private final PasswordEncoder passwords;
    private final CurrentAccount current;

    public AccountServiceImpl(UserRepository users, SellerRepository sellers, PasswordEncoder passwords, CurrentAccount current) {
        this.users = users; this.sellers = sellers; this.passwords = passwords; this.current = current;
    }

    @Override public void register(Forms.Registration form) {
        String email = Text.clean(form.email()).toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) throw DomainException.invalid("This email is already registered. Try signing in.");
        if (form.password().getBytes(StandardCharsets.UTF_8).length > 72) throw DomainException.invalid("Password must be at most 72 UTF-8 bytes.");
        if (!form.role().equals("BUYER") && !form.role().equals("SELLER")) throw DomainException.forbidden();
        AppUser user = new AppUser();
        user.displayName = Text.clean(form.displayName()); user.email = email;
        user.passwordHash = passwords.encode(form.password()); user.role = Role.valueOf(form.role());
        users.saveAndFlush(user);
        if (user.getRole() == Role.SELLER) {
            SellerProfile seller = new SellerProfile(); seller.user = user;
            seller.storeName = user.getDisplayName() + " Studio";
            sellers.save(seller);
        }
    }

    @Override @PreAuthorize("isAuthenticated()") @Transactional(readOnly = true)
    public Views.UserView profile() {
        AppUser user = current.require();
        return new Views.UserView(user.getId(), user.getDisplayName(), user.getEmail(), user.getRole(), user.getActive(), user.getDemo());
    }

    @Override @PreAuthorize("isAuthenticated()")
    public void updateProfile(Forms.Profile form) {
        AppUser user = current.require();
        String email = Text.clean(form.email()).toLowerCase(Locale.ROOT);
        if (users.findByEmail(email).filter(other -> !other.getId().equals(user.getId())).isPresent()) {
            throw DomainException.invalid("This email is already registered.");
        }
        user.displayName = Text.clean(form.displayName()); user.email = email;
        users.saveAndFlush(user);
    }
}
