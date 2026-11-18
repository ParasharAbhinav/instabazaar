package com.instabazaar.video;

import com.instabazaar.entity.AppUser;
import com.instabazaar.enums.Role;
import com.instabazaar.exception.DomainException;
import com.instabazaar.repository.UserRepository;
import com.instabazaar.security.AccountPrincipal;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:video-worker-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
        "instabazaar.video.work-path=target/video-worker-tests"})
@ActiveProfiles("test")
class VideoWorkerIntegrationTest {
    @Autowired VideoJobs videos;
    @Autowired VideoJobRepository jobs;
    @Autowired UserRepository users;
    @Autowired VideoFiles files;
    @MockitoBean VideoAnalyzer analyzer;
    private AppUser owner;

    @BeforeEach void setup() {
        owner = seller(); authenticate(owner);
        when(analyzer.available()).thenReturn(true);
        when(analyzer.analyze(any())).thenReturn(result());
    }
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test void committedUploadRunsInBackgroundAndTemporaryVideoIsRemoved() {
        Long id = videos.submit(video(), null, true, true);
        await().atMost(Duration.ofSeconds(15)).until(() -> jobs.findById(id).orElseThrow().getStatus() == VideoJob.Status.READY);
        var job = jobs.findById(id).orElseThrow();
        assertThat(job.getSuggestions()).contains("dress", "blue"); assertThat(job.getFrameCount()).isEqualTo(4);
        assertThat(job.getApprovedTags()).isEmpty();
        await().atMost(Duration.ofSeconds(5)).until(() -> Files.notExists(files.directory(job.getStorageKey())));
    }

    @Test void failedAnalysisReportsSafeFailureAndRemovesTheUpload() {
        when(analyzer.analyze(any())).thenThrow(new VideoProcessingException("Use a video shorter than 60 seconds."));
        Long id = videos.submit(video(), null, true, true);
        await().atMost(Duration.ofSeconds(15)).until(() -> jobs.findById(id).orElseThrow().getStatus() == VideoJob.Status.FAILED);
        var job = jobs.findById(id).orElseThrow();
        assertThat(job.getMessage()).contains("60 seconds");
        await().atMost(Duration.ofSeconds(5)).until(() -> Files.notExists(files.directory(job.getStorageKey())));
    }

    @Test void missingToolsAndUnconfirmedRightsDoNotCreateJobs() {
        long before = jobs.count();
        assertThatThrownBy(() -> videos.submit(video(), null, false, true)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> videos.submit(video(), null, true, false)).isInstanceOf(DomainException.class);
        when(analyzer.available()).thenReturn(false);
        assertThatThrownBy(() -> videos.submit(video(), null, true, true)).isInstanceOf(DomainException.class).hasMessageContaining("missing");
        assertThat(jobs.count()).isEqualTo(before);
        verify(analyzer, never()).analyze(any());
    }

    @Test void onlyOneAnalysisCanBePendingPerSeller() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        when(analyzer.analyze(any())).thenAnswer(invocation -> { started.countDown(); if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test worker timed out"); return result(); });
        Long id = videos.submit(video(), null, true, true);
        try {
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> videos.submit(video(), null, true, true)).isInstanceOf(DomainException.class).hasMessageContaining("previous video");
        } finally { release.countDown(); }
        await().atMost(Duration.ofSeconds(10)).until(() -> jobs.findById(id).orElseThrow().getStatus() == VideoJob.Status.READY);
    }

    @Test void interruptedJobsBecomeRetryableFailuresAtStartupRecovery() {
        String key = files.store(video());
        VideoJob job = new VideoJob(); job.owner = owner; job.storageKey = key; job.status = VideoJob.Status.PROCESSING;
        Long id = jobs.saveAndFlush(job).getId();
        videos.recoverInterrupted();
        assertThat(jobs.findById(id).orElseThrow().getStatus()).isEqualTo(VideoJob.Status.FAILED);
        assertThat(files.directory(key)).doesNotExist();
    }

    @Test void dailyLimitIsEnforcedWithoutRunningMoreInference() {
        for (int index = 0; index < 10; index++) {
            VideoJob job = new VideoJob(); job.owner = owner; job.storageKey = UUID.randomUUID().toString(); job.status = VideoJob.Status.FAILED; jobs.saveAndFlush(job);
        }
        assertThatThrownBy(() -> videos.submit(video(), null, true, true)).isInstanceOf(DomainException.class).hasMessageContaining("ten video");
        verify(analyzer, never()).analyze(any());
    }

    @Test void nativeLibraryFailuresDoNotLeaveJobsPendingOrExposePrivatePaths() {
        when(analyzer.analyze(any())).thenThrow(new UnsatisfiedLinkError("private/native/library/path"));
        Long id = videos.submit(video(), null, true, true);
        await().atMost(Duration.ofSeconds(15)).until(() -> jobs.findById(id).orElseThrow().getStatus() == VideoJob.Status.FAILED);
        var job = jobs.findById(id).orElseThrow();
        assertThat(job.getMessage()).contains("offline setup").doesNotContain("private/native");
        await().atMost(Duration.ofSeconds(5)).until(() -> Files.notExists(files.directory(job.getStorageKey())));
    }

    private AppUser seller() {
        AppUser user = new AppUser(); user.displayName = "Video worker seller"; user.email = UUID.randomUUID() + "@example.test";
        user.passwordHash = "not-a-login-fixture"; user.role = Role.SELLER; return users.saveAndFlush(user);
    }
    private void authenticate(AppUser user) {
        var principal = AccountPrincipal.from(user);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
    private MockMultipartFile video() { return new MockMultipartFile("video", "clip.mp4", "video/mp4", VideoFilesTest.mp4Header()); }
    private VideoAnalyzer.Analysis result() { return new VideoAnalyzer.Analysis(3.0, 4, List.of(new VisualTagger.Suggestion("Product type", "dress", 0.9), new VisualTagger.Suggestion("Colour", "blue", 0.8))); }
}
