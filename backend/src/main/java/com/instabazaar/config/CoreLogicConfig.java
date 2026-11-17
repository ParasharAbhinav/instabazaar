package com.instabazaar.config;

import java.time.Clock;
import java.util.Map;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.*;

@Configuration
public class CoreLogicConfig {
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    var pbkdf2 =
        new Pbkdf2PasswordEncoder(
            "", 16, 600000, Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
    String id = "pbkdf2-sha256-600k-v1";
    return new DelegatingPasswordEncoder(id, Map.of(id, pbkdf2));
  }
}
