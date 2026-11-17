package com.instabazaar.controller;

import com.instabazaar.dto.SearchQuery;
import com.instabazaar.exception.DomainException;
import com.instabazaar.service.*;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class CatalogController {
    private final CatalogService catalog;
    private final SearchService search;
    private final BuyerService buyers;
    public CatalogController(CatalogService catalog, SearchService search, BuyerService buyers) {
        this.catalog = catalog; this.search = search; this.buyers = buyers;
    }
    @GetMapping("/") public String home(Model model) {
        model.addAttribute("featured", search.search(SearchQuery.empty()).products().stream().limit(6).toList());
        return "home";
    }
    @GetMapping("/products") public String products(@Valid @ModelAttribute("filters") SearchQuery filters, BindingResult errors, Model model) {
        if (errors.hasErrors()) throw DomainException.invalid("Check your search filters and try again.");
        model.addAttribute("results", search.search(filters));
        return "catalogue";
    }
    @GetMapping("/categories/{id}") public String category(@PathVariable Long id) {
        if (catalog.categories().stream().noneMatch(category -> category.id().equals(id))) throw DomainException.missing();
        return "redirect:/products?categoryId=" + id;
    }
    @GetMapping("/products/{id}") public String product(@PathVariable Long id, Model model) {
        var product = catalog.product(id);
        model.addAttribute("product", product);
        model.addAttribute("store", catalog.seller(product.sellerId()));
        model.addAttribute("reviews", buyers.reviews(id));
        model.addAttribute("saved", buyers.saved(id));
        return "product";
    }
    @GetMapping("/sellers") public String sellers(@RequestParam(defaultValue = "") String q, Model model) {
        model.addAttribute("stores", catalog.sellers(q)); model.addAttribute("query", q);
        return "stores";
    }
    @GetMapping("/sellers/{id}") public String seller(@PathVariable Long id, @RequestParam(defaultValue = "0") Integer page, Model model) {
        model.addAttribute("store", catalog.seller(id));
        model.addAttribute("results", search.search(new SearchQuery("", null, id, null, null, "", "", null, "newest", page)));
        return "store";
    }
    @GetMapping("/sellers/{id}/contact/{kind}") public String contact(@PathVariable Long id, @PathVariable String kind, Model model) {
        var store = catalog.seller(id);
        String destination = switch (kind) {
            case "whatsapp" -> store.whatsappUrl(); case "instagram" -> store.instagramUrl(); case "facebook" -> store.facebookUrl();
            default -> throw DomainException.missing();
        };
        if (destination == null || destination.isBlank()) throw DomainException.missing();
        model.addAttribute("store", store); model.addAttribute("destination", destination); model.addAttribute("platform", kind);
        return "contact";
    }
}
