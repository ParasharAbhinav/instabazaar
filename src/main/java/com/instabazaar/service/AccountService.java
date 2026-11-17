package com.instabazaar.service;

import com.instabazaar.dto.Forms;
import com.instabazaar.dto.Views;
import jakarta.validation.Valid;

public interface AccountService {
    void register(@Valid Forms.Registration form);
    Views.UserView profile();
    void updateProfile(@Valid Forms.Profile form);
}
