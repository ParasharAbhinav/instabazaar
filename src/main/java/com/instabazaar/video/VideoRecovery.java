package com.instabazaar.video;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class VideoRecovery implements ApplicationRunner {
    private final VideoJobs jobs;
    public VideoRecovery(VideoJobs jobs) { this.jobs = jobs; }
    @Override public void run(ApplicationArguments arguments) { jobs.recoverInterrupted(); }
}
