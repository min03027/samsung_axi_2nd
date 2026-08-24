package com.ssa.lms.review.web;

import com.ssa.lms.review.entity.*;
import com.ssa.lms.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v2/api/reviews")
@RequiredArgsConstructor
public class PublicReviewController {
    private final ReviewService reviewService;

    @GetMapping
    public List<PublicReviewView> list(@RequestParam(required = false) ReviewExposureSite site,
                                       @RequestParam(required = false) ReviewExposurePosition position,
                                       @RequestParam(required = false) Long courseId,
                                       @RequestParam(required = false) Long organizationId,
                                       @RequestParam(required = false) String jobTitle,
                                       @RequestParam(required = false) Integer completionYear,
                                       @RequestParam(required = false) ReviewContentType contentType,
                                       @RequestParam(required = false) Boolean featured) {
        return reviewService.publicReviews(site, position, courseId, organizationId, jobTitle,
                completionYear, contentType, featured);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PublicReviewView> detail(@PathVariable Long id) {
        return reviewService.publicReview(id).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
