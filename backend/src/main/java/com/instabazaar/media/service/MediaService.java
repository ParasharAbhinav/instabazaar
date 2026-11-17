package com.instabazaar.media.service;

import com.instabazaar.account.model.Role;
import com.instabazaar.media.model.*;
import com.instabazaar.media.repository.MediaRepository;
import com.instabazaar.media.storage.MediaStorage;
import com.instabazaar.security.CurrentUser;
import com.instabazaar.shared.dto.Contracts;
import com.instabazaar.shared.exception.BusinessException;
import com.instabazaar.shared.repository.EntityAccess;
import java.io.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MediaService {
  private final CurrentUser current;
  private final MediaRepository assets;
  private final EntityAccess jpa;
  private final MediaStorage storage;
  private final ImageValidationService images;
  private final MediaUrls urls;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public MediaService(
      CurrentUser current,
      MediaRepository assets,
      EntityAccess jpa,
      MediaStorage storage,
      ImageValidationService images,
      MediaUrls urls,
      PlatformTransactionManager manager,
      Clock clock) {
    this.current = current;
    this.assets = assets;
    this.jpa = jpa;
    this.storage = storage;
    this.images = images;
    this.urls = urls;
    this.clock = clock;
    transactions = new TransactionTemplate(manager);
    transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Contracts.Asset upload(InputStream stream) {
    // Authorize before decoding attacker-controlled data; recheck in the metadata transaction.
    transactions.execute(status -> current.require(Role.SELLER).getId());
    var image = images.validate(stream);
    String key = UUID.randomUUID() + ".bin";
    try {
      storage.write(key, image.bytes());
    } catch (IOException ex) {
      throw unavailable(ex);
    }
    try {
      return transactions.execute(
          status -> {
            var asset =
                new MediaAsset(
                    current.require(Role.SELLER),
                    key,
                    image.contentType(),
                    image.bytes().length,
                    image.width(),
                    image.height());
            jpa.persist(asset);
            jpa.flush();
            return new Contracts.Asset(
                asset.getId(),
                urls.url(asset.getId()),
                asset.getContentType(),
                asset.getByteSize(),
                asset.getWidth(),
                asset.getHeight());
          });
    } catch (RuntimeException failure) {
      try {
        storage.delete(key);
      } catch (IOException cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      throw failure;
    }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public MediaAsset ownedReady(UUID id) {
    UUID owner = current.require(Role.SELLER).getId();
    var asset = assets.lock(id);
    if (!asset.getOwner().getId().equals(owner)) throw BusinessException.missing();
    if (asset.getState() != MediaState.READY) throw BusinessException.transition();
    return asset;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void requireFreeImage(UUID asset, UUID product) {
    if (assets.imageUsedElsewhere(asset, product))
      throw new BusinessException("ASSET_IN_USE", 409, "Asset is attached to another product.");
  }

  @Transactional(readOnly = true)
  public AuthorizedMedia authorizeRead(UUID id) {
    var asset = assets.get(id);
    if (asset.getState() != MediaState.READY) throw BusinessException.missing();
    if (!assets.publicAttachment(id)) {
      try {
        if (!current.require().getId().equals(asset.getOwner().getId()))
          throw BusinessException.missing();
      } catch (BusinessException ex) {
        throw BusinessException.missing();
      }
    }
    return new AuthorizedMedia(asset.getStorageKey(), asset.getContentType(), asset.getByteSize());
  }

  /** Internal stream descriptor; never serialize this record as an API response. */
  public record AuthorizedMedia(String storageKey, String contentType, long byteSize) {}

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void delete(UUID id) {
    String key =
        transactions.execute(
            status -> {
              var actor = current.require(Role.SELLER);
              var asset = assets.lock(id);
              if (!asset.getOwner().getId().equals(actor.getId()))
                throw BusinessException.missing();
              return markDeleting(asset);
            });
    deleteBytesAndMetadata(id, key);
  }

  private String markDeleting(MediaAsset asset) {
    if (assets.referenced(asset.getId()))
      throw new BusinessException("ASSET_IN_USE", 409, "Asset is still referenced.");
    asset.setState(MediaState.DELETING);
    jpa.flush();
    return asset.getStorageKey();
  }

  private void deleteBytesAndMetadata(UUID id, String key) {
    try {
      storage.delete(key);
    } catch (IOException ex) {
      throw unavailable(ex);
    }
    transactions.executeWithoutResult(
        status -> {
          var asset = assets.lock(id);
          if (asset.getState() != MediaState.DELETING || assets.referenced(id))
            throw BusinessException.transition();
          jpa.remove(asset);
          jpa.flush();
        });
  }

  /** Trusted maintenance entry point, never a user-facing operation. */
  public int cleanup() {
    Instant before = clock.instant().minus(Duration.ofHours(24));
    var candidates = transactions.execute(status -> assets.cleanupCandidates(before, 100));
    int removed = 0;
    for (UUID id : Objects.requireNonNull(candidates)) {
      try {
        String key =
            transactions.execute(
                status -> {
                  var asset = assets.lock(id);
                  if (asset.getState() != MediaState.DELETING
                      && !asset.getCreatedAt().isBefore(before)) return null;
                  return markDeleting(asset);
                });
        if (key != null) {
          deleteBytesAndMetadata(id, key);
          removed++;
        }
      } catch (BusinessException ex) {
        if (!Set.of("ASSET_IN_USE", "RESOURCE_NOT_FOUND", "SERVICE_UNAVAILABLE")
            .contains(ex.getCode())) throw ex;
      }
    }
    try {
      for (var key : storage.oldKeys(before, 100)) {
        if (Boolean.FALSE.equals(transactions.execute(status -> assets.keyExists(key.key())))) {
          storage.delete(key.key());
          removed++;
        }
      }
    } catch (IOException ex) {
      throw unavailable(ex);
    }
    return removed;
  }

  private BusinessException unavailable(IOException cause) {
    var error =
        new BusinessException(
            "SERVICE_UNAVAILABLE", 503, "Media storage is temporarily unavailable.");
    error.initCause(cause);
    return error;
  }
}
