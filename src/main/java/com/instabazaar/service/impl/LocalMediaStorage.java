package com.instabazaar.service.impl;

import com.instabazaar.exception.DomainException;
import com.instabazaar.service.MediaStorage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalMediaStorage implements MediaStorage {
    private static final Pattern KEY = Pattern.compile("[0-9a-f-]{36}\\.(jpg|png|webp)");
    private final Path root;

    public LocalMediaStorage(@Value("${instabazaar.storage-path}") String directory) throws IOException {
        Path directoryPath = Path.of(directory).toAbsolutePath().normalize();
        Files.createDirectories(directoryPath);
        root = directoryPath.toRealPath();
    }

    @Override public StoredImage store(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 5 * 1024 * 1024) {
            throw DomainException.invalid("Upload a JPEG, PNG or WebP image no larger than 5 MB.");
        }
        try {
            byte[] bytes;
            try (var input = file.getInputStream()) { bytes = input.readNBytes(5 * 1024 * 1024 + 1); }
            if (bytes.length > 5 * 1024 * 1024) throw DomainException.invalid("Image exceeds 5 MB.");
            String format;
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw DomainException.invalid("The file is not a supported image.");
                var reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!format.equals("jpeg") && !format.equals("png") && !format.equals("webp")) {
                        throw DomainException.invalid("Only JPEG, PNG and WebP images are accepted.");
                    }
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width < 1 || height < 1 || (long) width * height > 20_000_000L) {
                        throw DomainException.invalid("Image dimensions must not exceed 20 megapixels.");
                    }
                    BufferedImage decoded = reader.read(0);
                    if (decoded == null) throw DomainException.invalid("The image is damaged.");
                } finally { reader.dispose(); }
            }
            String extension = format.equals("jpeg") ? "jpg" : format;
            String key = UUID.randomUUID() + "." + extension;
            Files.write(resolve(key), bytes, StandardOpenOption.CREATE_NEW);
            return new StoredImage(key, "image/" + format);
        } catch (IOException exception) {
            throw DomainException.invalid("The image could not be read or stored. Try another image.");
        }
    }

    @Override public Resource load(String key) {
        Path path = resolve(key);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw DomainException.missing();
        return new FileSystemResource(path);
    }

    @Override public void delete(String key) {
        try { Files.deleteIfExists(resolve(key)); }
        catch (IOException exception) { throw new IllegalStateException("Could not remove stored image."); }
    }

    private Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) throw DomainException.missing();
        Path resolved = root.resolve(key).normalize();
        if (!resolved.getParent().equals(root) || Files.isSymbolicLink(resolved)) throw DomainException.missing();
        return resolved;
    }
}
