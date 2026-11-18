package com.instabazaar.video;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VideoSettings {
    public static final long MAX_BYTES = 30L * 1024 * 1024;
    public static final int MAX_SECONDS = 60;
    public static final int MAX_PIXELS = 1920 * 1080;
    private final Path models;
    private final Path work;
    private final String ffmpeg;
    private final String ffprobe;

    public VideoSettings(@Value("${instabazaar.video.model-path:./data/models/clip-vit-base-patch32}") String models,
                         @Value("${instabazaar.video.work-path:./data/video-work}") String work,
                         @Value("${instabazaar.video.ffmpeg:}") String ffmpeg,
                         @Value("${instabazaar.video.ffprobe:}") String ffprobe) {
        this.models = Path.of(models).toAbsolutePath().normalize();
        this.work = Path.of(work).toAbsolutePath().normalize();
        this.ffmpeg = executable(ffmpeg, "ffmpeg"); this.ffprobe = executable(ffprobe, "ffprobe");
    }

    public Path models() { return models; }
    public Path work() { return work; }
    public String ffmpeg() { return ffmpeg; }
    public String ffprobe() { return ffprobe; }

    private String executable(String configured, String name) {
        if (!configured.isBlank()) return configured;
        boolean windows = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
        Path bundled = Path.of("data/tools/ffmpeg/ffmpeg-9.0.2-essentials_build/bin", name + ".exe").toAbsolutePath().normalize();
        return windows && Files.isRegularFile(bundled) ? bundled.toString() : name;
    }
}
