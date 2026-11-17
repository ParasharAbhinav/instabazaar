package com.instabazaar.controller;

import com.instabazaar.dto.Forms;
import com.instabazaar.service.BuyerService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class BuyerController {
    private final BuyerService buyers;
    public BuyerController(BuyerService buyers) { this.buyers = buyers; }
    @GetMapping("/wishlist") public String wishlist(Model model) { model.addAttribute("products", buyers.wishlist()); return "wishlist"; }
    @PostMapping("/wishlist/{id}") public String save(@PathVariable Long id, RedirectAttributes flash) {
        buyers.save(id); flash.addFlashAttribute("success", "Saved to your wishlist."); return "redirect:/products/" + id;
    }
    @PostMapping("/wishlist/{id}/remove") public String remove(@PathVariable Long id) {
        buyers.remove(id); return "redirect:/wishlist";
    }
    @PostMapping("/products/{id}/reviews") public String review(@PathVariable Long id, @Valid @ModelAttribute Forms.ReviewInput form, RedirectAttributes flash) {
        buyers.review(id, form); flash.addFlashAttribute("success", "Thank you. Your review is published."); return "redirect:/products/" + id + "#reviews";
    }
    @PostMapping("/reviews/{id}/delete") public String removeReview(@PathVariable Long id, RedirectAttributes flash) {
        buyers.removeReview(id); flash.addFlashAttribute("success", "Your review is removed."); return "redirect:/products";
    }
}
