package com.instabazaar.video;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class VideoFiles {
    private final Path root;
    public VideoFiles(VideoSettings settings) throws IOException {
        Files.createDirectories(settings.work()); root = settings.work().toRealPath();
    }
    public String store(MultipartFile video) {
        if (video == null || video.isEmpty() || video.getSize() > VideoSettings.MAX_BYTES) {
            throw new VideoProcessingException("Choose an MP4 video no larger than 30 MB.");
        }
        String key = UUID.randomUUID().toString();
        Path directory = directory(key);
        try {
            Files.createDirectory(directory);
            try (var input = video.getInputStream(); var output = Files.newOutputStream(directory.resolve("source.mp4"), StandardOpenOption.CREATE_NEW)) {
                byte[] buffer = new byte[65536]; long total = 0; int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > VideoSettings.MAX_BYTES) throw new VideoProcessingException("The video exceeds 30 MB.");
                    output.write(buffer, 0, count);
                }
            }
            byte[] header;
            try (var input = Files.newInputStream(directory.resolve("source.mp4"))) { header = input.readNBytes(16); }
            if (header.length < 16 || !new String(header, 4, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("ftyp")) {
                throw new VideoProcessingException("This is not a supported MP4 file. Renaming a file to .mp4 is not enough.");
            }
            String brand = new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (!List.of("isom", "iso2", "iso3", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "M4V ", "dash", "MSNV", "av01").contains(brand)) {
                throw new VideoProcessingException("Use a standard MP4 export, not a QuickTime/MOV or unsupported container.");
            }
            return key;
        } catch (IOException exception) {
            cleanup(key); throw new VideoProcessingException("The video could not be stored. Check private storage space and permissions.");
        } catch (RuntimeException exception) { cleanup(key); throw exception; }
    }
    public Path directory(String key) {
        try { if (!UUID.fromString(key).toString().equals(key)) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException | NullPointerException exception) { throw new VideoProcessingException("Invalid video storage key."); }
        Path directory = root.resolve(key).normalize();
        if (!directory.getParent().equals(root) || Files.isSymbolicLink(directory)) throw new VideoProcessingException("Invalid video storage directory.");
        return directory;
    }
    public void cleanup(String key) {
        Path directory = directory(key);
        try {
            for (String file : List.of("source.mp4", "probe.json", "frame-0.jpg", "frame-1.jpg", "frame-2.jpg", "frame-3.jpg")) Files.deleteIfExists(directory.resolve(file));
            Files.deleteIfExists(directory);
        } catch (IOException exception) {
            org.slf4j.LoggerFactory.getLogger(VideoFiles.class).warn("Private temporary video cleanup requires attention.");
        }
    }

    public void cleanupAbandonedDirectories() {
        try (var entries = Files.list(root)) {
            entries.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)).forEach(path -> {
                String key = path.getFileName().toString();
                try { if (UUID.fromString(key).toString().equals(key)) cleanup(key); }
                catch (IllegalArgumentException exception) { }
            });
        } catch (IOException exception) {
            org.slf4j.LoggerFactory.getLogger(VideoFiles.class).warn("Startup cleanup of private video files requires attention.");
        }
    }
}
