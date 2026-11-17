package com.instabazaar.media.controller;

import com.instabazaar.media.service.ImageValidationService;
import com.instabazaar.media.service.MediaService;
import com.instabazaar.media.storage.MediaStorage;
import com.instabazaar.shared.dto.Contracts;
import com.instabazaar.shared.exception.BusinessException;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping("/api/v1")
public class MediaController {
  private final MediaService media;
  private final MediaStorage storage;

  public MediaController(MediaService media, MediaStorage storage) {
    this.media = media;
    this.storage = storage;
  }

  @PostMapping(value = "/seller/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Contracts.Asset> upload(
      @RequestPart("file") MultipartFile file, MultipartHttpServletRequest request)
      throws ServletException, IOException {
    if (request.getParts().size() != 1) {
      throw BusinessException.invalid("file", "Supply exactly one file part.");
    }
    if (file.isEmpty()) throw BusinessException.invalid("file", "File must not be empty.");
    if (file.getSize() > ImageValidationService.MAX_BYTES) {
      throw new BusinessException("UPLOAD_TOO_LARGE", 413, "Upload exceeds 5 MiB.");
    }
    try (var stream = file.getInputStream()) {
      var asset = media.upload(stream);
      return ResponseEntity.created(URI.create("/api/v1/media/" + asset.id())).body(asset);
    }
  }

  @DeleteMapping("/seller/media/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    media.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/media/{id}")
  public ResponseEntity<byte[]> read(@PathVariable UUID id) {
    // The service authorizes private previews or public attachments before bytes are opened.
    var authorized = media.authorizeRead(id);
    try (var stream = storage.read(authorized.storageKey())) {
      byte[] bytes = stream.readNBytes(ImageValidationService.MAX_BYTES + 1);
      if (bytes.length != authorized.byteSize())
        throw new IOException("Stored media size mismatch.");
      return ResponseEntity.ok()
          .contentType(MediaType.parseMediaType(authorized.contentType()))
          .contentLength(bytes.length)
          .cacheControl(CacheControl.noStore())
          .header(
              HttpHeaders.CONTENT_DISPOSITION,
              ContentDisposition.inline().filename(id.toString()).build().toString())
          .body(bytes);
    } catch (IOException failure) {
      var error =
          new BusinessException(
              "SERVICE_UNAVAILABLE", 503, "Media storage is temporarily unavailable.");
      error.initCause(failure);
      throw error;
    }
  }
}
