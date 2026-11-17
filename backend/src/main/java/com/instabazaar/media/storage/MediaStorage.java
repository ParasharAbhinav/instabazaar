package com.instabazaar.media.storage;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;

/**
 * Private storage adapter. Deployment provides an object-storage implementation of this contract.
 */
public interface MediaStorage {
  record StoredKey(String key, Instant modifiedAt) {}

  void write(String key, byte[] bytes) throws IOException;

  InputStream read(String key) throws IOException;

  void delete(String key) throws IOException;

  List<StoredKey> oldKeys(Instant before, int limit) throws IOException;
}
