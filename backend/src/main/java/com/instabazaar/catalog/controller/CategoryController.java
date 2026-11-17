package com.instabazaar.catalog.controller;

import com.instabazaar.catalog.service.CategoryService;
import com.instabazaar.shared.dto.Contracts;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
  private final CategoryService categories;

  public CategoryController(CategoryService categories) {
    this.categories = categories;
  }

  @GetMapping
  public Contracts.Items<Contracts.Category> list() {
    return categories.active();
  }
}
