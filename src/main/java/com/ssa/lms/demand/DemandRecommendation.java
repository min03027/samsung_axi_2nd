package com.ssa.lms.demand;
import com.ssa.lms.common.entity.BaseEntity;
import com.ssa.lms.content.entity.ContentLibraryItem;
import com.ssa.lms.course.entity.Course;
import com.ssa.lms.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="demand_recommendation",uniqueConstraints=@UniqueConstraint(name="uk_demand_signal_course",columnNames={"signal_id","course_id"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class DemandRecommendation extends BaseEntity{
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="signal_id",nullable=false) private DemandSignal signal;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="course_id",nullable=false) private Course course;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="library_item_id") private ContentLibraryItem suggestedLibraryItem;
 @Column(name="match_score",nullable=false) private int matchScore;
 @Column(name="matched_keywords",nullable=false,length=500) private String matchedKeywords;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private DemandRecommendationStatus status;
 @Column(name="review_note",columnDefinition="TEXT") private String reviewNote;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="reviewed_by") private User reviewedBy;
 @Column(name="reviewed_at") private LocalDateTime reviewedAt;
 @Builder private DemandRecommendation(DemandSignal signal,Course course,ContentLibraryItem suggestedLibraryItem,int matchScore,String matchedKeywords){this.signal=signal;this.course=course;this.suggestedLibraryItem=suggestedLibraryItem;this.matchScore=matchScore;this.matchedKeywords=matchedKeywords;this.status=DemandRecommendationStatus.PENDING;}
 public void review(DemandRecommendationStatus status,String note,User actor){this.status=status;this.reviewNote=note;this.reviewedBy=actor;this.reviewedAt=LocalDateTime.now();}
}
