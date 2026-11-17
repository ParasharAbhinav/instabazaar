package com.instabazaar.controller;

import com.instabazaar.exception.DomainException;
import com.instabazaar.service.AdminService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequestMapping("/admin")
public class AdminController {
    private final AdminService admin;
    public AdminController(AdminService admin) { this.admin = admin; }
    @GetMapping public String dashboard(Model model) { model.addAttribute("stats", admin.stats()); return "admin/dashboard"; }
    @GetMapping("/users") public String users(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("users", admin.users(page)); model.addAttribute("page", page); return "admin/users";
    }
    @GetMapping("/products") public String products(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("products", admin.products(page)); model.addAttribute("page", page); return "admin/products";
    }
    @GetMapping("/reviews") public String reviews(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("reviews", admin.reviews(page)); model.addAttribute("page", page); return "admin/reviews";
    }
    @PostMapping("/users/{id}/status") public String user(@PathVariable Long id, @RequestParam boolean active,
            @RequestParam(defaultValue = "false") boolean confirm, RedirectAttributes flash) {
        confirmation(confirm); admin.setUserActive(id, active); flash.addFlashAttribute("success", "Account status updated."); return "redirect:/admin/users";
    }
    @PostMapping("/products/{id}/visibility") public String product(@PathVariable Long id, @RequestParam boolean hidden,
            @RequestParam(defaultValue = "false") boolean confirm, RedirectAttributes flash) {
        confirmation(confirm); admin.setProductHidden(id, hidden); flash.addFlashAttribute("success", "Product visibility updated."); return "redirect:/admin/products";
    }
    @PostMapping("/reviews/{id}/visibility") public String review(@PathVariable Long id, @RequestParam boolean hidden,
            @RequestParam(defaultValue = "false") boolean confirm, RedirectAttributes flash) {
        confirmation(confirm); admin.setReviewHidden(id, hidden); flash.addFlashAttribute("success", "Review visibility updated."); return "redirect:/admin/reviews";
    }
    private void confirmation(boolean confirm) { if (!confirm) throw DomainException.invalid("Confirm the moderation action first."); }
}
