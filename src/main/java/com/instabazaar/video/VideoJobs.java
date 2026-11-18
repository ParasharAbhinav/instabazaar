package com.instabazaar.video;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instabazaar.dto.Forms;
import com.instabazaar.entity.Product;
import com.instabazaar.exception.DomainException;
import com.instabazaar.repository.*;
import com.instabazaar.security.CurrentAccount;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VideoJobs {
    public record JobView(Long id, VideoJob.Status status, Long productId, List<VisualTagger.Suggestion> suggestions,
                          String approvedTags, String message, int frameCount, double duration) {
        public boolean pending() { return status == VideoJob.Status.QUEUED || status == VideoJob.Status.PROCESSING; }
        public boolean approved(String label) { return Arrays.asList(approvedTags.split(", ")).contains(label); }
    }
    private static final List<VideoJob.Status> PENDING = List.of(VideoJob.Status.QUEUED, VideoJob.Status.PROCESSING);
    private final VideoJobRepository jobs;
    private final UserRepository users;
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final CurrentAccount current;
    private final VideoFiles files;
    private final VideoAnalyzer analyzer;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    private volatile boolean accepting;

    public VideoJobs(VideoJobRepository jobs, UserRepository users, ProductRepository products, CategoryRepository categories,
                     CurrentAccount current, VideoFiles files, VideoAnalyzer analyzer, ObjectMapper json, PlatformTransactionManager manager) {
        this.jobs = jobs; this.users = users; this.products = products; this.categories = categories;
        this.current = current; this.files = files; this.analyzer = analyzer; this.json = json;
        transaction = new TransactionTemplate(manager); transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        executor.setCorePoolSize(1); executor.setMaxPoolSize(1); executor.setQueueCapacity(2);
        executor.setThreadNamePrefix("offline-video-"); executor.setDaemon(true);
        executor.setWaitForTasksToCompleteOnShutdown(false); executor.setAwaitTerminationSeconds(5); executor.initialize();
    }

    public boolean available() { return accepting && analyzer.available(); }

    @Transactional(readOnly = true) @PreAuthorize("hasRole('SELLER')")
    public List<JobView> recent() { return jobs.findTop10ByOwnerIdOrderByCreatedAtDesc(current.require().getId()).stream().map(this::view).toList(); }

    @Transactional(readOnly = true) @PreAuthorize("hasRole('SELLER')")
    public JobView get(Long id) { return view(owned(id, false)); }

    @Transactional @PreAuthorize("hasRole('SELLER')")
    public Long submit(MultipartFile video, Long productId, boolean rights, boolean singleProduct) {
        var owner = users.lockById(current.require().getId()).orElseThrow(DomainException::forbidden);
        if (!rights || !singleProduct) throw DomainException.invalid("Confirm that you may use this video and that it shows one product.");
        if (!available()) throw DomainException.invalid("Offline video tools or models are missing. Run scripts/setup-video.ps1 on the application computer first.");
        if (jobs.existsByOwnerIdAndStatusIn(owner.getId(), PENDING)) throw DomainException.invalid("Your previous video is still processing. Wait for it to finish.");
        if (jobs.countByOwnerIdAndCreatedAtAfter(owner.getId(), Instant.now().minusSeconds(86400)) >= 10) throw DomainException.invalid("The local demo allows ten video analyses per seller per 24 hours.");
        Product product = productId == null ? null : products.findById(productId).orElseThrow(DomainException::missing);
        if (product != null && !product.getSeller().getUser().getId().equals(owner.getId())) throw DomainException.forbidden();
        final String key;
        try { key = files.store(video); }
        catch (VideoProcessingException exception) { throw DomainException.invalid(exception.getMessage()); }
        VideoJob job = new VideoJob(); job.owner = owner; job.product = product; job.storageKey = key;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) { files.cleanup(key); return; }
                try { executor.execute(() -> process(job.getId(), key)); }
                catch (TaskRejectedException exception) { fail(job.getId(), "The local analyser is busy. Please try uploading again shortly."); files.cleanup(key); }
            }
        });
        return jobs.saveAndFlush(job).getId();
    }

    @Transactional @PreAuthorize("hasRole('SELLER')")
    public JobView review(Long id, List<String> selected) {
        VideoJob job = owned(id, true);
        if (job.getStatus() != VideoJob.Status.READY && job.getStatus() != VideoJob.Status.REVIEWED) throw DomainException.invalid("This video is not ready for review.");
        Set<String> allowed = new HashSet<>(decode(job).stream().map(VisualTagger.Suggestion::label).toList());
        Set<String> chosen = new LinkedHashSet<>(selected == null ? List.of() : selected);
        if (chosen.isEmpty() || chosen.size() > 24 || !allowed.containsAll(chosen)) throw DomainException.invalid("Select at least one of this video's suggested tags, or continue with manual entry instead.");
        job.approvedTags = String.join(", ", chosen); job.status = VideoJob.Status.REVIEWED;
        job.message = "Tags approved. Review the product form and save it to make these keywords searchable.";
        return view(job);
    }

    @Transactional(readOnly = true) @PreAuthorize("hasRole('SELLER')")
    public Forms.ProductInput prefill(Forms.ProductInput form, Long jobId, Long productId) {
        VideoJob job = reviewed(jobId, productId, false);
        Set<String> tags = new LinkedHashSet<>();
        for (String label : form.tags().split(",")) if (!label.isBlank()) tags.add(label.strip());
        tags.addAll(Arrays.asList(job.getApprovedTags().split(", ")));
        if (tags.size() > 24) throw DomainException.invalid("This listing already has too many seller tags. Remove some before adding video tags.");
        String colour = form.colour(), title = form.title(); Long categoryId = form.categoryId();
        if (productId == null) {
            String type = approved(job, "Product type"); colour = approved(job, "Colour");
            title = (colour + " " + type).strip();
            if (!title.isBlank()) title = Character.toUpperCase(title.charAt(0)) + title.substring(1);
            String category = switch (type) { case "dress" -> "Dresses"; case "kurti" -> "Kurtis"; case "saree" -> "Ethnic wear"; case "top", "t shirt" -> "Tops"; default -> "Other garments"; };
            categoryId = categories.findByName(category).orElseThrow(DomainException::missing).getId();
        }
        return new Forms.ProductInput(title, form.description(), form.price(), categoryId, colour, form.sizes(), form.material(), form.occasion(), form.brand(), form.stock(), form.status(), form.sourceUrl(), form.caption(), String.join(", ", tags), form.version());
    }

    @Transactional @PreAuthorize("hasRole('SELLER')")
    public void attach(Long jobId, Long previousProductId, Product saved) {
        VideoJob job = reviewed(jobId, previousProductId, true);
        if (!saved.getSeller().getUser().getId().equals(current.require().getId())) throw DomainException.forbidden();
        job.product = saved; job.status = VideoJob.Status.APPLIED;
        job.message = "Product saved. The tags you kept on the product form are now included in catalogue search.";
    }

    public void recoverInterrupted() {
        accepting = false;
        var keys = transaction.execute(status -> jobs.findByStatusIn(PENDING).stream().map(job -> {
            job.status = VideoJob.Status.FAILED; job.message = "Processing stopped when the application restarted. Upload the video again.";
            return job.getStorageKey();
        }).toList());
        if (keys != null) keys.forEach(files::cleanup);
        files.cleanupAbandonedDirectories();
        accepting = true;
    }

    private void process(Long id, String key) {
        try {
            Boolean run = transaction.execute(status -> {
                VideoJob job = jobs.lockById(id).orElseThrow(DomainException::missing);
                if (job.getStatus() != VideoJob.Status.QUEUED) return false;
                if (!job.getOwner().getActive()) { job.status = VideoJob.Status.FAILED; job.message = "The account is inactive."; return false; }
                job.status = VideoJob.Status.PROCESSING; job.message = "Extracting four frames and analysing them locally. The first run may take longer."; return true;
            });
            if (!Boolean.TRUE.equals(run)) return;
            var analysis = analyzer.analyze(files.directory(key));
            String payload = json.writeValueAsString(analysis.suggestions());
            transaction.executeWithoutResult(status -> {
                VideoJob job = jobs.lockById(id).orElseThrow(DomainException::missing);
                job.suggestions = payload; job.duration = analysis.duration(); job.frameCount = analysis.frameCount(); job.status = VideoJob.Status.READY;
                job.message = analysis.suggestions().isEmpty() ? "No sufficiently distinct clothing tags were found. Try a clearer single-product video or add details manually." : "Review these visual suggestions. Nothing has been added to public search yet.";
            });
        } catch (Exception | LinkageError exception) {
            org.slf4j.LoggerFactory.getLogger(VideoJobs.class).warn("Offline video job {} failed ({})", id, exception.getClass().getSimpleName());
            fail(id, exception instanceof VideoProcessingException ? exception.getMessage() : "Local visual analysis could not finish. Verify the offline setup and try a clear single-product MP4.");
        } finally { files.cleanup(key); }
    }

    private void fail(Long id, String message) {
        transaction.executeWithoutResult(status -> jobs.findById(id).ifPresent(job -> { job.status = VideoJob.Status.FAILED; job.message = message; }));
    }
    private VideoJob reviewed(Long id, Long productId, boolean lock) {
        VideoJob job = owned(id, lock);
        Long target = job.getProduct() == null ? null : job.getProduct().getId();
        if (job.getStatus() != VideoJob.Status.REVIEWED || !Objects.equals(target, productId)) throw DomainException.invalid("Approve this video's tags for the correct product before using them.");
        return job;
    }
    private VideoJob owned(Long id, boolean lock) {
        VideoJob job = (lock ? jobs.lockById(id) : jobs.findById(id)).orElseThrow(DomainException::missing);
        if (!job.getOwner().getId().equals(current.require().getId())) throw DomainException.forbidden();
        return job;
    }
    private List<VisualTagger.Suggestion> decode(VideoJob job) {
        try { return json.readValue(job.getSuggestions(), new TypeReference<>() {}); }
        catch (java.io.IOException exception) { throw new IllegalStateException("Stored visual suggestions could not be read.", exception); }
    }
    private String approved(VideoJob job, String group) {
        Set<String> selected = Set.copyOf(Arrays.asList(job.getApprovedTags().split(", ")));
        return decode(job).stream().filter(tag -> tag.group().equals(group) && selected.contains(tag.label())).map(VisualTagger.Suggestion::label).findFirst().orElse("");
    }
    private JobView view(VideoJob job) {
        return new JobView(job.getId(), job.getStatus(), job.getProduct() == null ? null : job.getProduct().getId(), decode(job), job.getApprovedTags(), job.getMessage(), job.getFrameCount(), job.getDuration());
    }
    @PreDestroy public void shutdown() { executor.shutdown(); }
}
