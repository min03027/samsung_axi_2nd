package com.ssa.lms.demand;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface DemandRecommendationRepository extends JpaRepository<DemandRecommendation,Long>{List<DemandRecommendation> findBySignalIdOrderByMatchScoreDesc(Long signalId);Optional<DemandRecommendation> findBySignalIdAndCourseId(Long signalId,Long courseId);long countByStatus(DemandRecommendationStatus status);}
