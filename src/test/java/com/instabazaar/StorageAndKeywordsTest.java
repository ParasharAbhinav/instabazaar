package com.instabazaar;

import com.instabazaar.exception.DomainException;
import com.instabazaar.service.impl.*;
import com.instabazaar.util.ExternalLinks;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;

class StorageAndKeywordsTest {
    @TempDir Path directory;

    @Test void keywordsAreDeterministicAndUseFashionAliases() {
        var service = new KeywordTagSuggestionService();
        List<String> tags = service.suggest("Ivory cotton top, olive handloom kurta, evening embroidery");
        assertThat(tags).contains("cotton", "top", "white", "green", "kurti", "party wear", "embroidered");
        assertThat(service.suggest("Ivory cotton top, olive handloom kurta, evening embroidery")).isEqualTo(tags);
        assertThat(service.suggest("topology redacted silken")).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"https://instagram.com.attacker.example/post/1", "javascript:alert(1)", "http://instagram.com/post/1", "https://evil@instagram.com/post/1", "https://instagram.com:8443/post/1", "//instagram.com/post/1", "https://localhost/"})
    void unsafeExternalLinksAreRejected(String url) {
        assertThatThrownBy(() -> ExternalLinks.validate(url, "social media")).isInstanceOf(DomainException.class);
    }

    @Test void onlySupportedDomainsAndInternationalWhatsappNumbersAreAccepted() {
        assertThat(ExternalLinks.validate("https://www.instagram.com/reel/example/", "social media")).contains("instagram.com");
        assertThat(ExternalLinks.validate("https://wa.me/919876543210", "whatsapp")).endsWith("919876543210");
        assertThatThrownBy(() -> ExternalLinks.validate("https://wa.me/+919876543210", "whatsapp")).isInstanceOf(DomainException.class);
        assertThat(ExternalLinks.validate("https://api.whatsapp.com/send?phone=919876543210", "whatsapp")).contains("phone=");
        assertThatThrownBy(() -> ExternalLinks.validate("https://api.whatsapp.com/send?phone=not-a-number", "whatsapp")).isInstanceOf(DomainException.class);
    }

    @Test void uploadedFilenamesNeverControlStoragePaths() throws Exception {
        var storage = new LocalMediaStorage(directory.toString());
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "jpeg", bytes);
        var stored = storage.store(new MockMultipartFile("image", "../../outside.jpg", "image/jpeg", bytes.toByteArray()));
        assertThat(stored.key()).matches("[0-9a-f-]{36}\\.jpg");
        assertThat(storage.load(stored.key()).exists()).isTrue();
        storage.delete(stored.key());
        assertThatThrownBy(() -> storage.load(stored.key())).isInstanceOf(DomainException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"../secret.png", "..\\secret.jpg", "/etc/passwd", "C:\\secret.png", "not-an-id.svg"})
    void traversalKeysAreRejected(String key) throws Exception {
        var storage = new LocalMediaStorage(directory.toString());
        assertThatThrownBy(() -> storage.load(key)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> storage.delete(key)).isInstanceOf(DomainException.class);
    }

    @Test void fakeAndOversizedImagesAreRejected() throws Exception {
        var storage = new LocalMediaStorage(directory.toString());
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("image", "fake.png", "image/png", "<script>bad</script>".getBytes()))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("image", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]))).isInstanceOf(DomainException.class);
    }

    @Test void actualWebpContentIsAccepted() throws Exception {
        var storage = new LocalMediaStorage(directory.toString());
        byte[] bytes = java.util.Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        var stored = storage.store(new MockMultipartFile("image", "pixel.webp", "image/webp", bytes));
        assertThat(stored.contentType()).isEqualTo("image/webp");
        assertThat(stored.key()).endsWith(".webp");
    }
}
