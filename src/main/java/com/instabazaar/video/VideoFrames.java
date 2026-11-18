package com.instabazaar.video;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
public class VideoFrames {
    public record Frames(double duration, List<BufferedImage> images) {}
    private final VideoSettings settings;
    private final ObjectMapper json;
    public VideoFrames(VideoSettings settings, ObjectMapper json) { this.settings = settings; this.json = json; }

    public boolean available() { return exists(settings.ffmpeg()) && exists(settings.ffprobe()); }

    public Frames extract(Path directory) {
        Path source = directory.resolve("source.mp4");
        Path probe = directory.resolve("probe.json");
        try {
            run(List.of(settings.ffprobe(), "-v", "error", "-protocol_whitelist", "file,pipe", "-probesize", "5000000", "-analyzeduration", "5000000",
                    "-show_entries", "stream=codec_type,width,height:format=duration,format_name", "-of", "json", source.toString()), directory, probe, 15);
            if (Files.size(probe) > 32768) throw new VideoProcessingException("This video has too many streams or invalid metadata.");
            var metadata = json.readTree(probe.toFile());
            double duration = metadata.path("format").path("duration").asDouble(Double.NaN);
            if (!Double.isFinite(duration) || duration < 0.2 || duration > VideoSettings.MAX_SECONDS) throw new VideoProcessingException("Use a video between 0.2 and 60 seconds long.");
            var streams = metadata.path("streams");
            int videoCount = 0;
            for (var stream : streams) {
                if (!stream.path("codec_type").asText().equals("video")) continue;
                videoCount++;
                int width = stream.path("width").asInt(), height = stream.path("height").asInt();
                if (width < 64 || height < 64 || (long) width * height > VideoSettings.MAX_PIXELS || Math.max(width, height) > Math.min(width, height) * 6L) {
                    throw new VideoProcessingException("Use a normal portrait or landscape video up to 1920×1080 pixels (or 1080×1920).");
                }
            }
            if (videoCount != 1) throw new VideoProcessingException("Use an MP4 with exactly one video stream.");
            List<BufferedImage> frames = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                Path frame = directory.resolve("frame-" + index + ".jpg");
                double position = duration * (0.1 + index * 0.25);
                run(List.of(settings.ffmpeg(), "-nostdin", "-v", "error", "-y", "-protocol_whitelist", "file,pipe", "-threads", "1",
                        "-ss", String.format(Locale.ROOT, "%.3f", position), "-i", source.toString(), "-map", "0:v:0", "-an", "-sn", "-dn", "-frames:v", "1",
                        "-filter_threads", "1", "-vf", "scale=512:512:force_original_aspect_ratio=decrease", frame.toString()), directory, null, 15);
                if (!Files.isRegularFile(frame) || Files.size(frame) > 2 * 1024 * 1024) throw new VideoProcessingException("The video frames could not be decoded safely.");
                BufferedImage image = ImageIO.read(frame.toFile());
                if (image == null || image.getWidth() > 512 || image.getHeight() > 512) throw new VideoProcessingException("The video has unreadable frames.");
                frames.add(image);
            }
            return new Frames(duration, List.copyOf(frames));
        } catch (IOException exception) { throw new VideoProcessingException("The video could not be decoded. Try a standard MP4 export."); }
    }

    private void run(List<String> command, Path directory, Path output, int seconds) throws IOException {
        Process process = new ProcessBuilder(command).directory(directory.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectOutput(output == null ? ProcessBuilder.Redirect.DISCARD : ProcessBuilder.Redirect.to(output.toFile())).start();
        try {
            if (!process.waitFor(seconds, TimeUnit.SECONDS)) {
                process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS);
                throw new VideoProcessingException("Video decoding timed out. Try a shorter or simpler MP4.");
            }
            if (process.exitValue() != 0) throw new VideoProcessingException("The MP4 is damaged, unsupported or could not be decoded.");
        } catch (InterruptedException exception) {
            process.destroyForcibly(); Thread.currentThread().interrupt(); throw new VideoProcessingException("Video processing was interrupted. Upload it again.");
        }
    }
    private boolean exists(String executable) {
        Path path = Path.of(executable);
        if (path.isAbsolute() || path.getNameCount() > 1) return Files.isRegularFile(path);
        String search = System.getenv("PATH");
        if (search == null) return false;
        for (String folder : search.split(java.io.File.pathSeparator)) {
            if (Files.isRegularFile(Path.of(folder).resolve(executable)) || Files.isRegularFile(Path.of(folder).resolve(executable + ".exe"))) return true;
        }
        return false;
    }
}
