package com.instabazaar.video;

import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class VideoAnalyzer {
    public record Analysis(double duration, int frameCount, List<VisualTagger.Suggestion> suggestions) {}
    private final VideoFrames frames;
    private final VisualTagger tagger;
    public VideoAnalyzer(VideoFrames frames, VisualTagger tagger) { this.frames = frames; this.tagger = tagger; }
    public boolean available() { return frames.available() && tagger.available(); }
    public Analysis analyze(Path directory) {
        var extracted = frames.extract(directory);
        return new Analysis(extracted.duration(), extracted.images().size(), tagger.suggest(extracted.images()));
    }
}
