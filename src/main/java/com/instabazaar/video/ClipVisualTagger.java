package com.instabazaar.video;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.*;
import jakarta.annotation.PreDestroy;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ClipVisualTagger implements VisualTagger, AutoCloseable {
    public static final String MODEL = "CLIP ViT-B/32 · local INT8 · 6b7c523";
    private record Candidate(String group, String label, String prompt) {}
    private record Embedded(Candidate candidate, float[] vector) {}
    private static final float[] MEAN = {0.48145466f, 0.4578275f, 0.40821073f};
    private static final float[] STD = {0.26862954f, 0.26130258f, 0.27577711f};
    private static final List<Candidate> CANDIDATES = vocabulary();
    private final VideoSettings settings;
    private OrtEnvironment environment;
    private OrtSession vision;
    private OrtSession text;
    private HuggingFaceTokenizer tokenizer;
    private List<Embedded> vocabulary;
    private final Map<String, List<Embedded>> attributeVocabulary = new HashMap<>();

    public ClipVisualTagger(VideoSettings settings) { this.settings = settings; }

    @Override public boolean available() {
        return List.of("vision_model_quantized.onnx", "text_model_quantized.onnx", "tokenizer.json").stream()
                .allMatch(name -> Files.isRegularFile(settings.models().resolve(name)));
    }

    @Override public synchronized List<Suggestion> suggest(List<BufferedImage> frames) {
        if (frames.isEmpty() || frames.size() > 4) throw new IllegalArgumentException("Provide one to four frames.");
        try {
            initialize();
            float[] average = new float[512];
            for (BufferedImage frame : frames) {
                try (var tensor = OnnxTensor.createTensor(environment, preprocess(frame));
                     var result = vision.run(Map.of("pixel_values", tensor))) {
                    float[] embedding = embedding(result, "image_embeds");
                    normalize(embedding);
                    for (int index = 0; index < average.length; index++) average[index] += embedding[index] / frames.size();
                }
            }
            normalize(average);
            List<Suggestion> suggestions = new ArrayList<>();
            String productType = "";
            for (String group : List.of("Product type", "Colour", "Appearance")) {
                List<Embedded> candidates;
                if (group.equals("Product type")) candidates = vocabulary;
                else {
                    candidates = attributeVocabulary.get(productType);
                    if (candidates == null) {
                        String type = productType;
                        candidates = embed(CANDIDATES.stream().filter(candidate -> !candidate.group.equals("Product type"))
                                .map(candidate -> new Candidate(candidate.group, candidate.label, candidate.prompt.replace("{product}", type))).toList());
                        attributeVocabulary.put(productType, candidates);
                    }
                }
                var entries = candidates.stream().filter(entry -> entry.candidate.group.equals(group)).toList();
                double[] scores = entries.stream().mapToDouble(entry -> cosine(average, entry.vector)).toArray();
                int best = 0;
                for (int index = 1; index < scores.length; index++) if (scores[index] > scores[best]) best = index;
                double denominator = 0;
                for (double score : scores) denominator += Math.exp((score - scores[best]) * 100);
                double match = 1 / denominator;
                double second = 0;
                for (int index = 0; index < scores.length; index++) if (index != best) second = Math.max(second, Math.exp((scores[index] - scores[best]) * 100) / denominator);
                String label = entries.get(best).candidate.label;
                org.slf4j.LoggerFactory.getLogger(ClipVisualTagger.class).debug("Visual ranking {}: label={}, cosine={}, score={}, margin={}", group, label, scores[best], match, match - second);
                double minimum = group.equals("Appearance") ? 0.60 : 0.35;
                boolean accepted = !label.isEmpty() && scores[best] >= 0.18 && match >= minimum && match - second >= 0.08;
                if (accepted) {
                    suggestions.add(new Suggestion(group, label, Math.round(match * 1000) / 1000.0));
                    if (group.equals("Product type")) productType = label;
                }
                else if (group.equals("Product type")) return List.of();
            }
            return List.copyOf(suggestions);
        } catch (OrtException | java.io.IOException exception) {
            throw new IllegalStateException("The local visual model could not run. Verify the offline model installation.", exception);
        }
    }

    private void initialize() throws OrtException, java.io.IOException {
        if (vocabulary != null) return;
        if (!available()) throw new IllegalStateException("Offline models are missing. Run scripts/setup-video.ps1 first.");
        close();
        environment = OrtEnvironment.getEnvironment();
        try (var options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(2); options.setInterOpNumThreads(1);
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            vision = environment.createSession(settings.models().resolve("vision_model_quantized.onnx").toString(), options);
            text = environment.createSession(settings.models().resolve("text_model_quantized.onnx").toString(), options);
        }
        tokenizer = HuggingFaceTokenizer.newInstance(settings.models().resolve("tokenizer.json"), Map.of("maxLength", "77", "modelMaxLength", "77"));
        vocabulary = embed(CANDIDATES.stream().filter(candidate -> candidate.group.equals("Product type")).toList());
    }

    private List<Embedded> embed(List<Candidate> candidates) throws OrtException {
        List<Embedded> embeddings = new ArrayList<>();
        for (Candidate candidate : candidates) {
            long[] encoded = tokenizer.encode(candidate.prompt).getIds();
            if (encoded.length > 77) throw new IllegalStateException("Visual vocabulary prompt is too long.");
            long[][] ids = new long[1][77]; Arrays.fill(ids[0], 49407);
            long[][] attention = new long[1][77];
            System.arraycopy(encoded, 0, ids[0], 0, encoded.length); Arrays.fill(attention[0], 0, encoded.length, 1);
            try (var idTensor = OnnxTensor.createTensor(environment, ids);
                 var attentionTensor = OnnxTensor.createTensor(environment, attention)) {
                Map<String, OnnxTensor> inputs = new HashMap<>(); inputs.put("input_ids", idTensor);
                if (text.getInputNames().contains("attention_mask")) inputs.put("attention_mask", attentionTensor);
                try (var result = text.run(inputs)) {
                    float[] vector = embedding(result, "text_embeds"); normalize(vector);
                    embeddings.add(new Embedded(candidate, vector));
                }
            }
        }
        return List.copyOf(embeddings);
    }

    static float[][][][] preprocess(BufferedImage source) {
        if (Math.max(source.getWidth(), source.getHeight()) > Math.min(source.getWidth(), source.getHeight()) * 8L) throw new IllegalArgumentException("Frame aspect ratio is too extreme.");
        double factor = 224.0 / Math.min(source.getWidth(), source.getHeight());
        int width = Math.max(224, (int) Math.round(source.getWidth() * factor));
        int height = Math.max(224, (int) Math.round(source.getHeight() * factor));
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try { graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC); graphics.drawImage(source, 0, 0, width, height, null); }
        finally { graphics.dispose(); }
        float[][][][] pixels = new float[1][3][224][224];
        int left = (width - 224) / 2, top = (height - 224) / 2;
        for (int row = 0; row < 224; row++) {
            for (int column = 0; column < 224; column++) {
                int rgb = resized.getRGB(left + column, top + row);
                for (int channel = 0; channel < 3; channel++) pixels[0][channel][row][column] = (((rgb >> (16 - channel * 8)) & 255) / 255f - MEAN[channel]) / STD[channel];
            }
        }
        return pixels;
    }

    private float[] embedding(OrtSession.Result result, String name) throws OrtException {
        Object value = result.get(name).orElseThrow(() -> new IllegalStateException("Unexpected CLIP model output.")).getValue();
        if (!(value instanceof float[][] values) || values.length != 1 || values[0].length != 512) throw new IllegalStateException("Unexpected CLIP embedding shape.");
        return values[0].clone();
    }
    private static void normalize(float[] values) {
        double length = 0; for (float value : values) length += value * value;
        length = Math.sqrt(length);
        if (length == 0 || !Double.isFinite(length)) throw new IllegalStateException("Invalid visual embedding.");
        for (int index = 0; index < values.length; index++) values[index] /= (float) length;
    }
    private static double cosine(float[] first, float[] second) {
        double result = 0; for (int index = 0; index < first.length; index++) result += first[index] * second[index]; return result;
    }
    private static List<Candidate> vocabulary() {
        List<Candidate> result = new ArrayList<>();
        Map<String, String> types = new LinkedHashMap<>();
        types.put("dress", "a dress"); types.put("kurti", "a kurti, an Indian tunic"); types.put("saree", "a saree, traditional Indian clothing");
        types.put("shirt", "a shirt"); types.put("t shirt", "a t-shirt"); types.put("top", "a blouse or top");
        types.put("jacket", "a jacket"); types.put("trousers", "trousers or pants"); types.put("skirt", "a skirt");
        types.forEach((label, description) -> result.add(new Candidate("Product type", label, "a product photo of " + description)));
        result.add(new Candidate("Product type", "", "a photo of scenery, an animal, food, or an object that is not clothing"));
        for (String colour : List.of("red", "blue", "green", "black", "white", "pink", "yellow", "orange", "brown", "purple", "grey", "beige")) {
            result.add(new Candidate("Colour", colour, "a photo of a " + colour + " {product}"));
        }
        for (String pattern : List.of("plain", "floral", "striped", "checked", "polka dot", "embroidered")) {
            result.add(new Candidate("Appearance", pattern, "a photo of a " + pattern + " {product}"));
        }
        result.add(new Candidate("Appearance", "", "a photo of a {product} with an unclear pattern"));
        return List.copyOf(result);
    }
    @Override @PreDestroy public synchronized void close() {
        vocabulary = null;
        attributeVocabulary.clear();
        if (tokenizer != null) { tokenizer.close(); tokenizer = null; }
        try { if (vision != null) vision.close(); } catch (OrtException ignored) { }
        try { if (text != null) text.close(); } catch (OrtException ignored) { }
        vision = null; text = null;
    }
}
