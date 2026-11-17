package com.instabazaar.media.storage;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;

public class LocalMediaStorage implements MediaStorage {
  private final Path root;
  private String scanAfter = "";

  public LocalMediaStorage(Path root) throws IOException {
    Files.createDirectories(root);
    this.root = root.toRealPath();
  }

  private Path path(String key) {
    if (key == null || !key.matches("[0-9a-f-]{36}\\.bin"))
      throw new IllegalArgumentException("Invalid internal storage key.");
    Path p = root.resolve(key).normalize();
    if (!p.getParent().equals(root) || Files.isSymbolicLink(p))
      throw new IllegalArgumentException("Invalid storage location.");
    return p;
  }

  public void write(String key, byte[] bytes) throws IOException {
    Path target = path(key);
    Path temporary = Files.createTempFile(root, "upload-", ".tmp");
    try {
      Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING);
      Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  public InputStream read(String key) throws IOException {
    return Files.newInputStream(path(key), LinkOption.NOFOLLOW_LINKS);
  }

  public void delete(String key) throws IOException {
    Files.deleteIfExists(path(key));
  }

  public synchronized List<StoredKey> oldKeys(Instant before, int limit) throws IOException {
    if (limit < 1 || limit > 1000) throw new IllegalArgumentException("Invalid scan limit.");
    var result = new TreeMap<String, StoredKey>();
    try (var entries = Files.newDirectoryStream(root)) {
      for (Path p : entries) {
        if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) continue;
        String name = p.getFileName().toString();
        Instant modified = Files.getLastModifiedTime(p, LinkOption.NOFOLLOW_LINKS).toInstant();
        if (name.compareTo(scanAfter) > 0
            && modified.isBefore(before)
            && name.matches("[0-9a-f-]{36}\\.bin")) {
          result.put(name, new StoredKey(name, modified));
          if (result.size() > limit) result.pollLastEntry();
        }
        if (modified.isBefore(before) && name.startsWith("upload-") && name.endsWith(".tmp"))
          Files.deleteIfExists(p);
      }
    }
    scanAfter = result.isEmpty() ? "" : result.lastKey();
    return List.copyOf(result.values());
  }

  @Configuration
  static class LocalStorageConfiguration {
    @Bean
    @ConditionalOnMissingBean(MediaStorage.class)
    @Profile("!production")
    MediaStorage localMediaStorage(
        @Value("${instabazaar.media.directory:./var/media}") String directory) throws IOException {
      return new LocalMediaStorage(Path.of(directory));
    }
  }
}
