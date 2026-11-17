package com.instabazaar.controller;

import com.instabazaar.dto.Forms;
import com.instabazaar.exception.DomainException;
import com.instabazaar.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AccountController {
    private final AccountService accounts;
    public AccountController(AccountService accounts) { this.accounts = accounts; }

    @GetMapping("/login") public String login() { return "auth/login"; }
    @GetMapping("/register") public String register(Model model) { model.addAttribute("form", Forms.Registration.empty()); return "auth/register"; }
    @PostMapping("/register") public String register(@Valid @ModelAttribute("form") Forms.Registration form, BindingResult errors) {
        if (!errors.hasErrors()) {
            try { accounts.register(form); return "redirect:/login?registered"; }
            catch (DomainException exception) { errors.reject("registration", exception.getMessage()); }
        }
        return "auth/register";
    }
    @GetMapping("/account") public String account() {
        return switch (accounts.profile().role()) {
            case SELLER -> "redirect:/seller/dashboard"; case ADMIN -> "redirect:/admin"; default -> "redirect:/wishlist";
        };
    }
    @GetMapping("/profile") public String profile(Model model) {
        var profile = accounts.profile(); model.addAttribute("form", new Forms.Profile(profile.displayName(), profile.email()));
        return "profile";
    }
    @PostMapping("/profile") public String profile(@Valid @ModelAttribute("form") Forms.Profile form, BindingResult errors, RedirectAttributes flash) {
        if (!errors.hasErrors()) {
            try { accounts.updateProfile(form); flash.addFlashAttribute("success", "Your profile is updated."); return "redirect:/profile"; }
            catch (DomainException exception) { errors.reject("profile", exception.getMessage()); }
        }
        return "profile";
    }
    @GetMapping("/access-denied") public String denied(Model model, jakarta.servlet.http.HttpServletResponse response) {
        response.setStatus(403); model.addAttribute("status", 403); model.addAttribute("message", "You do not have access to this page."); return "error";
    }
}
