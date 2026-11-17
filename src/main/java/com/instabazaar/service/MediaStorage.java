package com.instabazaar.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface MediaStorage {
    record StoredImage(String key, String contentType) {}
    StoredImage store(MultipartFile file);
    Resource load(String key);
    void delete(String key);
}
