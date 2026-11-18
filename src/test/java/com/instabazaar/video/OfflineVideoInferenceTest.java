package com.instabazaar.video;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;

@EnabledIfSystemProperty(named = "video.model.tests", matches = "true")
class OfflineVideoInferenceTest {
    @TempDir Path temporary;

    @ParameterizedTest @CsvSource({"dress.mp4, blue", "red-dress.mp4, red"})
    void actualMp4ProducesVisualDressAndColourKeywordsWithoutHostedInference(String filename, String colour) throws Exception {
        Path video = Path.of(System.getProperty("video.test.directory", "data/video-demo"), filename);
        assertThat(video).isRegularFile();
        var settings = new VideoSettings("data/models/clip-vit-base-patch32", temporary.toString(), "", "");
        var storage = new VideoFiles(settings);
        String key = storage.store(new MockMultipartFile("video", "misleading-name.mp4", "video/mp4", Files.readAllBytes(video)));
        try (var tagger = new ClipVisualTagger(settings)) {
            var analyzer = new VideoAnalyzer(new VideoFrames(settings, new ObjectMapper()), tagger);
            assertThat(analyzer.available()).isTrue();
            long start = System.nanoTime();
            var result = analyzer.analyze(storage.directory(key));
            System.out.println("OFFLINE VIDEO RESULT: " + result + "; elapsed seconds=" + (System.nanoTime() - start) / 1_000_000_000.0);
            assertThat(result.frameCount()).isEqualTo(4);
            assertThat(result.duration()).isBetween(0.2, 60.0);
            assertThat(result.suggestions()).extracting(VisualTagger.Suggestion::label).contains("dress", colour);
        } finally { storage.cleanup(key); }
        assertThat(storage.directory(key)).doesNotExist();
    }

    @Test void fakeMp4ContainerIsRejectedByRealDecoder() throws Exception {
        var settings = new VideoSettings("data/models/clip-vit-base-patch32", temporary.toString(), "", "");
        var storage = new VideoFiles(settings);
        String key = storage.store(new MockMultipartFile("video", "fake.mp4", "video/mp4", VideoFilesTest.mp4Header()));
        try { assertThatThrownBy(() -> new VideoFrames(settings, new ObjectMapper()).extract(storage.directory(key))).isInstanceOf(VideoProcessingException.class); }
        finally { storage.cleanup(key); }
    }
}
