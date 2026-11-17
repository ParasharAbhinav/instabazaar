package com.instabazaar.media.service;

import com.instabazaar.shared.exception.BusinessException;
import java.io.*;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Service;

@Service
public class ImageValidationService {
  public static final int MAX_BYTES = 5 * 1024 * 1024;

  public record ValidatedImage(byte[] bytes, String contentType, int width, int height) {}

  public ValidatedImage validate(InputStream stream) {
    if (stream == null) throw BusinessException.invalid("file", "Required.");
    try {
      byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
      if (bytes.length > MAX_BYTES)
        throw new BusinessException("UPLOAD_TOO_LARGE", 413, "Image exceeds 5 MiB.");
      if (bytes.length == 0) throw invalid();
      try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
        var readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext())
          throw new BusinessException("UNSUPPORTED_MEDIA_TYPE", 415, "Use JPEG, PNG or WebP.");
        var reader = readers.next();
        try {
          String contentType =
              switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                case "jpeg", "jpg" -> "image/jpeg";
                case "png" -> "image/png";
                case "webp" -> "image/webp";
                default ->
                    throw new BusinessException(
                        "UNSUPPORTED_MEDIA_TYPE", 415, "Use JPEG, PNG or WebP.");
              };
          reader.setInput(input, true, true);
          int width = reader.getWidth(0), height = reader.getHeight(0);
          if (width <= 0 || height <= 0 || (long) width * height > 20_000_000) throw invalid();
          var decoded = reader.read(0);
          if (decoded == null || decoded.getWidth() != width || decoded.getHeight() != height)
            throw invalid();
          decoded.flush();
          return new ValidatedImage(bytes, contentType, width, height);
        } finally {
          reader.dispose();
        }
      }
    } catch (IOException ex) {
      throw invalid();
    } catch (RuntimeException ex) {
      if (ex instanceof BusinessException business) throw business;
      throw invalid();
    }
  }

  private BusinessException invalid() {
    return new BusinessException(
        "INVALID_IMAGE", 422, "Image cannot be decoded or exceeds 20 megapixels.");
  }
}
