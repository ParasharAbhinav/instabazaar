package com.instabazaar.controller;

import com.instabazaar.dto.Views;
import com.instabazaar.security.CurrentAccount;
import com.instabazaar.service.CatalogService;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
public class PageAdvice {
    private final CatalogService catalog;
    private final CurrentAccount current;
    private final Environment environment;
    public PageAdvice(CatalogService catalog, CurrentAccount current, Environment environment) {
        this.catalog = catalog; this.current = current; this.environment = environment;
    }
    @ModelAttribute("categories") public List<Views.CategoryView> categories() { return catalog.categories(); }
    @ModelAttribute("currentUser") public Views.UserView currentUser() {
        var user = current.optional();
        return user == null ? null : new Views.UserView(user.getId(), user.getDisplayName(), user.getEmail(), user.getRole(), user.getActive(), user.getDemo());
    }
    @ModelAttribute("demoMode") public boolean demoMode() { return environment.matchesProfiles("demo", "dev"); }
}
