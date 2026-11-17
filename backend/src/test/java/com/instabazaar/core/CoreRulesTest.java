package com.instabazaar.core;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.instabazaar.config.CoreLogicConfig;
import com.instabazaar.enrichment.service.*;
import com.instabazaar.media.service.ImageValidationService;
import com.instabazaar.shared.dto.Contracts;
import com.instabazaar.shared.exception.BusinessException;
import com.instabazaar.shared.validation.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class CoreRulesTest {
  @Test
  void moneyAndPaginationRejectInvalidBoundaries() {
    assertThat(Rules.money(new BigDecimal("9999999999.99"), "price").toPlainString())
        .isEqualTo("9999999999.99");
    for (String input : List.of("-0.01", "1.001", "10000000000", "1E-2147483647"))
      assertThatThrownBy(() -> Rules.money(new BigDecimal(input), "price"))
          .isInstanceOf(BusinessException.class);
    assertThat(Rules.offset(100, 100)).isEqualTo(10000);
    assertThatThrownBy(() -> Rules.offset(Integer.MAX_VALUE, 100))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> Rules.offset(0, 101)).isInstanceOf(BusinessException.class);
    assertThatThrownBy(
            () -> Rules.text("\\u0000".replace("\\u0000", "\u0000"), "text", 10, false, false))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void socialUrlsRequireExactHostsAndNoCredentials() {
    var validator = new ExternalUrlValidator();
    assertThat(validator.validate("https://www.instagram.com/shop/", "instagramUrl"))
        .contains("instagram.com");
    for (String input :
        List.of(
            "http://instagram.com/a",
            "https://instagram.com.evil.test/",
            "https://evil@instagram.com/a",
            "https://instagram.com:444/a",
            "https://instagram.com./a",
            "https://facebook.com/a"))
      assertThatThrownBy(() -> validator.validate(input, "instagramUrl"))
          .isInstanceOf(BusinessException.class);
  }

  @Test
  void vocabularyIsDeterministicAndUsesWholeTokens() {
    var provider = new ControlledVocabularyTagProvider();
    var content =
        new TagSuggestionProvider.Content(
            "RED embroidered dress", null, "red", "cotton", null, null, "Party wear, embroidered.");
    assertThat(provider.suggest(content))
        .isEqualTo(provider.suggest(content))
        .contains("red", "embroidered", "dress", "cotton", "party wear");
    assertThat(
            provider.suggest(
                new TagSuggestionProvider.Content(
                    "address topping", null, null, null, null, null, null)))
        .isEmpty();
    assertThat(
            provider.suggest(
                new TagSuggestionProvider.Content("party", "wear", null, null, null, null, null)))
        .doesNotContain("party wear");
  }

  @Test
  void imageValidationDecodesRatherThanTrustingMime() throws Exception {
    var validator = new ImageValidationService();
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(8, 9, BufferedImage.TYPE_INT_RGB), "png", bytes);
    var image = validator.validate(new ByteArrayInputStream(bytes.toByteArray()));
    assertThat(image.contentType()).isEqualTo("image/png");
    assertThat(image.width()).isEqualTo(8);
    assertThat(image.height()).isEqualTo(9);
    assertThatThrownBy(
            () ->
                validator.validate(
                    new ByteArrayInputStream(new byte[ImageValidationService.MAX_BYTES + 1])))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("UPLOAD_TOO_LARGE"));
    assertThatThrownBy(
            () -> validator.validate(new ByteArrayInputStream("not an image".getBytes())))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void passwordEncoderUsesSaltVersionAndFullPassword() {
    var encoder = new CoreLogicConfig().passwordEncoder();
    String password = "correct horse battery staple";
    String first = encoder.encode(password), second = encoder.encode(password);
    assertThat(first).startsWith("{pbkdf2-sha256-600k-v1}").isNotEqualTo(second);
    assertThat(encoder.matches(password, first)).isTrue();
    assertThat(encoder.matches(password + " ", first)).isFalse();
  }

  @Test
  void emptyRatingAndPageSerializeWithExactKeys() {
    var mapper = JsonMapper.builder().build();
    var page = new Contracts.Page<>(List.of(new Contracts.Rating(null, 0)), 0, 20, 1, 1);
    var json = mapper.valueToTree(page);
    assertThat(json.properties().stream().map(Map.Entry::getKey))
        .containsExactlyInAnyOrder("items", "page", "size", "totalElements", "totalPages");
    assertThat(json.path("items").get(0).path("average").isNull()).isTrue();
  }

  @Test
  void jpegAndWebpDecodeAndOversizeDimensionsFailBeforeAllocation() throws Exception {
    var validator = new ImageValidationService();
    var jpeg = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB), "jpeg", jpeg);
    assertThat(validator.validate(new ByteArrayInputStream(jpeg.toByteArray())).contentType())
        .isEqualTo("image/jpeg");
    byte[] webp =
        Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
    assertThat(validator.validate(new ByteArrayInputStream(webp)).contentType())
        .isEqualTo("image/webp");
    var ihdr = new ByteArrayOutputStream();
    try (var output = new DataOutputStream(ihdr)) {
      output.writeBytes("IHDR");
      output.writeInt(5001);
      output.writeInt(4000);
      output.write(new byte[] {8, 2, 0, 0, 0});
    }
    var png = new ByteArrayOutputStream();
    try (var output = new DataOutputStream(png)) {
      output.write(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
      output.writeInt(13);
      output.write(ihdr.toByteArray());
      var crc = new java.util.zip.CRC32();
      crc.update(ihdr.toByteArray());
      output.writeInt((int) crc.getValue());
    }
    assertThatThrownBy(() -> validator.validate(new ByteArrayInputStream(png.toByteArray())))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_IMAGE"));
  }

  @Test
  void errorContractDoesNotExposeDatabaseOrFilesystemDetails() {
    var clock =
        java.time.Clock.fixed(
            java.time.Instant.parse("2026-10-11T00:00:00Z"), java.time.ZoneOffset.UTC);
    var mapper = new com.instabazaar.shared.exception.ServiceErrorMapper(clock);
    var failure =
        mapper.map(new RuntimeException("private password and filesystem path"), "trace-123");
    assertThat(failure.status()).isEqualTo(500);
    assertThat(failure.body().message()).doesNotContain("password", "filesystem");
    assertThat(failure.body().timestamp()).isEqualTo(clock.instant());
    assertThat(mapper.map(BusinessException.conflict(), "trace-123").status()).isEqualTo(409);
  }
}
