package com.instabazaar.controller;

import com.instabazaar.service.MediaService;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class MediaController {
    private final MediaService media;
    public MediaController(MediaService media) { this.media = media; }
    @GetMapping("/media/{key}") public ResponseEntity<Resource> image(@PathVariable String key) {
        var image = media.load(key);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff").body(image.resource());
    }
}
