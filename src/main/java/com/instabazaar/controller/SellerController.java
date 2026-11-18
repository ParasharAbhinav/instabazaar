package com.instabazaar.controller;

import com.instabazaar.dto.Forms;
import com.instabazaar.exception.DomainException;
import com.instabazaar.service.CatalogService;
import com.instabazaar.video.VideoJobs;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequestMapping("/seller")
public class SellerController {
    private final CatalogService catalog;
    private final VideoJobs videos;
    public SellerController(CatalogService catalog, VideoJobs videos) { this.catalog = catalog; this.videos = videos; }

    @GetMapping({"/dashboard", "/products"}) public String dashboard(Model model) {
        model.addAttribute("products", catalog.ownProducts()); model.addAttribute("store", catalog.ownStore());
        return "seller/dashboard";
    }
    @GetMapping("/store") public String store(Model model) {
        var store = catalog.ownStore();
        model.addAttribute("store", store);
        model.addAttribute("form", new Forms.Store(store.storeName(), store.description(), store.publicEmail(), store.whatsappUrl(), store.instagramUrl(), store.facebookUrl()));
        return "seller/store-form";
    }
    @PostMapping("/store") public String updateStore(@Valid @ModelAttribute("form") Forms.Store form, BindingResult errors,
            @RequestParam(required = false) MultipartFile logo, Model model, RedirectAttributes flash) {
        if (!errors.hasErrors()) {
            try { catalog.updateStore(form, logo); flash.addFlashAttribute("success", "Your storefront is updated."); return "redirect:/seller/store"; }
            catch (DomainException exception) { errors.reject("store", exception.getMessage()); }
        }
        model.addAttribute("store", catalog.ownStore()); return "seller/store-form";
    }
    @GetMapping("/products/new") public String create(@RequestParam(required = false) Long videoJobId, Model model) {
        Forms.ProductInput form = Forms.ProductInput.empty();
        if (videoJobId != null) form = videos.prefill(form, videoJobId, null);
        model.addAttribute("form", form); model.addAttribute("productId", null); model.addAttribute("videoJobId", videoJobId); return "seller/product-form";
    }
    @GetMapping("/products/{id}/edit") public String edit(@PathVariable Long id, @RequestParam(required = false) Long videoJobId, Model model) {
        Forms.ProductInput form = catalog.editor(id);
        if (videoJobId != null) form = videos.prefill(form, videoJobId, id);
        model.addAttribute("form", form); model.addAttribute("productId", id); model.addAttribute("product", catalog.ownProduct(id)); model.addAttribute("videoJobId", videoJobId);
        return "seller/product-form";
    }
    @PostMapping("/products") public String create(@Valid @ModelAttribute("form") Forms.ProductInput form, BindingResult errors,
            @RequestParam(required = false) MultipartFile image, @RequestParam(required = false) Long videoJobId, Model model, RedirectAttributes flash) {
        return save(null, form, errors, image, videoJobId, model, flash);
    }
    @PostMapping("/products/{id}") public String edit(@PathVariable Long id, @Valid @ModelAttribute("form") Forms.ProductInput form,
            BindingResult errors, @RequestParam(required = false) MultipartFile image, @RequestParam(required = false) Long videoJobId, Model model, RedirectAttributes flash) {
        catalog.ownProduct(id);
        return save(id, form, errors, image, videoJobId, model, flash);
    }
    private String save(Long id, Forms.ProductInput form, BindingResult errors, MultipartFile image, Long videoJobId, Model model, RedirectAttributes flash) {
        if (!errors.hasErrors()) {
            try { catalog.save(id, form, image, videoJobId); flash.addFlashAttribute("success", "Your product is saved."); return "redirect:/seller/products"; }
            catch (DomainException exception) {
                if (exception.getStatus().value() == 403 || exception.getStatus().value() == 404) throw exception;
                errors.reject("product", exception.getMessage());
            }
        }
        model.addAttribute("productId", id);
        model.addAttribute("videoJobId", videoJobId);
        if (id != null) model.addAttribute("product", catalog.ownProduct(id));
        return "seller/product-form";
    }
    @PostMapping("/products/{id}/archive") public String archive(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean confirm, RedirectAttributes flash) {
        if (!confirm) throw DomainException.invalid("Confirm before archiving a listing.");
        catalog.archive(id); flash.addFlashAttribute("success", "Product archived. Saved links will show it as unavailable."); return "redirect:/seller/products";
    }
    @PostMapping("/products/{id}/images/{key}/delete") public String removeImage(@PathVariable Long id, @PathVariable String key,
            @RequestParam(defaultValue = "false") boolean confirm) {
        if (!confirm) throw DomainException.invalid("Confirm before removing an image.");
        catalog.removeImage(id, key); return "redirect:/seller/products/" + id + "/edit";
    }
}
