package com.instabazaar.video;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;

class VideoFilesTest {
    @TempDir Path temporary;

    static byte[] mp4Header() { return new byte[]{0, 0, 0, 16, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 0, 0}; }

    @Test void originalNamesCannotControlPrivatePathsAndCleanupRemovesTemporaryData() throws Exception {
        var storage = storage();
        String key = storage.store(new MockMultipartFile("video", "../../escape.mp4", "video/mp4", mp4Header()));
        assertThat(key).matches("[0-9a-f-]{36}");
        assertThat(storage.directory(key).resolve("source.mp4")).isRegularFile();
        assertThat(temporary.resolve("escape.mp4")).doesNotExist();
        Files.writeString(storage.directory(key).resolve("probe.json"), "{}");
        storage.cleanup(key);
        assertThat(storage.directory(key)).doesNotExist();
    }

    @Test void renamedFakeFilesAndQuicktimeAreRejectedAndDeleted() throws Exception {
        var storage = storage();
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("video", "fake.mp4", "video/mp4", "not a video".getBytes())))
                .isInstanceOf(VideoProcessingException.class);
        byte[] quicktime = mp4Header(); quicktime[8] = 'q'; quicktime[9] = 't'; quicktime[10] = ' '; quicktime[11] = ' ';
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("video", "fake.mp4", "video/mp4", quicktime))).isInstanceOf(VideoProcessingException.class);
        try (var contents = Files.list(temporary)) { assertThat(contents.count()).isZero(); }
    }

    @Test void emptyAndOversizedUploadsAreRejectedBeforeStorage() throws Exception {
        var storage = storage();
        assertThatThrownBy(() -> storage.store(null)).isInstanceOf(VideoProcessingException.class);
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("video", new byte[0]))).isInstanceOf(VideoProcessingException.class);
        var oversized = new MockMultipartFile("video", "large.mp4", "video/mp4", mp4Header()) {
            @Override public long getSize() { return VideoSettings.MAX_BYTES + 1; }
        };
        assertThatThrownBy(() -> storage.store(oversized)).isInstanceOf(VideoProcessingException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"../outside", "..\\outside", "/etc/passwd", "C:\\outside", "not-a-uuid"})
    void arbitraryKeysCannotEscapePrivateStorage(String key) throws Exception {
        var storage = storage();
        assertThatThrownBy(() -> storage.directory(key)).isInstanceOf(VideoProcessingException.class);
        assertThatThrownBy(() -> storage.cleanup(key)).isInstanceOf(VideoProcessingException.class);
    }

    @Test void imagePreprocessingUsesClipRgbNormalisationAndCentreCrop() {
        BufferedImage image = new BufferedImage(16, 32, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics(); graphics.setColor(Color.RED); graphics.fillRect(0, 0, 16, 32); graphics.dispose();
        float[][][][] pixels = ClipVisualTagger.preprocess(image);
        assertThat(pixels.length).isEqualTo(1); assertThat(pixels[0].length).isEqualTo(3);
        assertThat(pixels[0][0].length).isEqualTo(224); assertThat(pixels[0][0][0].length).isEqualTo(224);
        assertThat(pixels[0][0][112][112]).isCloseTo((1f - 0.48145466f) / 0.26862954f, within(0.0001f));
        assertThat(pixels[0][1][112][112]).isCloseTo(-0.4578275f / 0.26130258f, within(0.0001f));
    }

    @Test void extremeAspectRatiosAreNotUpscaledIntoUnboundedBuffers() {
        assertThatThrownBy(() -> ClipVisualTagger.preprocess(new BufferedImage(1, 1000, BufferedImage.TYPE_INT_RGB))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void missingModelsAreReportedWithoutAutomaticNetworkDownloads() {
        try (var model = new ClipVisualTagger(new VideoSettings(temporary.toString(), temporary.toString(), "", ""))) {
            assertThat(model.available()).isFalse();
            assertThatThrownBy(() -> model.suggest(java.util.List.of(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB))))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("missing");
        }
    }

    @Test void startupCleanupRemovesOrphanedVideoFilesButLeavesUnrelatedDirectories() throws Exception {
        var storage = storage();
        String key = storage.store(new MockMultipartFile("video", "clip.mp4", "video/mp4", mp4Header()));
        Path unrelated = Files.createDirectory(temporary.resolve("not-a-video-job"));
        Files.writeString(unrelated.resolve("notes.txt"), "keep this");
        storage.cleanupAbandonedDirectories();
        assertThat(storage.directory(key)).doesNotExist();
        assertThat(unrelated.resolve("notes.txt")).hasContent("keep this");
    }

    private VideoFiles storage() throws Exception { return new VideoFiles(new VideoSettings(temporary.toString(), temporary.toString(), "", "")); }
}
