package com.instabazaar.video;

import com.instabazaar.service.CatalogService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller @RequestMapping("/seller/videos")
public class VideoController {
    private final VideoJobs jobs;
    private final CatalogService catalog;
    public VideoController(VideoJobs jobs, CatalogService catalog) { this.jobs = jobs; this.catalog = catalog; }

    @GetMapping({"", "/new"}) public String upload(@RequestParam(required = false) Long productId, Model model) {
        if (productId != null) model.addAttribute("product", catalog.ownProduct(productId));
        model.addAttribute("productId", productId); model.addAttribute("available", jobs.available()); model.addAttribute("recent", jobs.recent());
        return "seller/video-upload";
    }
    @PostMapping public String submit(@RequestParam MultipartFile video, @RequestParam(required = false) Long productId,
                                      @RequestParam(defaultValue = "false") boolean rights, @RequestParam(defaultValue = "false") boolean singleProduct) {
        return "redirect:/seller/videos/" + jobs.submit(video, productId, rights, singleProduct);
    }
    @GetMapping("/{id}") public String review(@PathVariable Long id, Model model) {
        model.addAttribute("job", jobs.get(id)); model.addAttribute("modelName", ClipVisualTagger.MODEL);
        return "seller/video-review";
    }
    @PostMapping("/{id}/review") public String review(@PathVariable Long id, @RequestParam(name = "tag", required = false) List<String> selected) {
        var job = jobs.review(id, selected);
        return job.productId() == null ? "redirect:/seller/products/new?videoJobId=" + id : "redirect:/seller/products/" + job.productId() + "/edit?videoJobId=" + id;
    }
}
