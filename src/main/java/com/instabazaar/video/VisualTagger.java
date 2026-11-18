package com.instabazaar.video;

import java.awt.image.BufferedImage;
import java.util.List;

public interface VisualTagger {
    record Suggestion(String group, String label, double score) {}
    List<Suggestion> suggest(List<BufferedImage> frames);
    boolean available();
}
