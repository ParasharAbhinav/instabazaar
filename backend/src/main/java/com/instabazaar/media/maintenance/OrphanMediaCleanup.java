package com.instabazaar.media.maintenance;

import com.instabazaar.media.service.MediaService;
import org.springframework.stereotype.Component;

@Component
public class OrphanMediaCleanup {
  private final MediaService media;

  public OrphanMediaCleanup(MediaService media) {
    this.media = media;
  }

  public int runBatch() {
    return media.cleanup();
  }
}
