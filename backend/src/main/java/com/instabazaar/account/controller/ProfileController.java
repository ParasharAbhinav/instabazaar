package com.instabazaar.account.controller;

import com.instabazaar.account.service.AccountService;
import com.instabazaar.shared.dto.Contracts;
import com.instabazaar.shared.web.Requests;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class ProfileController {
  private final AccountService accounts;

  public ProfileController(AccountService accounts) {
    this.accounts = accounts;
  }

  @GetMapping
  public Contracts.User me() {
    return accounts.me();
  }

  @PutMapping
  public Contracts.User update(@Valid @RequestBody Requests.Profile body) {
    return accounts.updateName(body.name(), body.version());
  }
}
