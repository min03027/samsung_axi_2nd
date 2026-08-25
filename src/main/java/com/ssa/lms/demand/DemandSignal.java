package com.ssa.lms.demand;
import com.ssa.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
@Entity @Table(name="demand_signal",indexes={@Index(name="idx_demand_signal_observed",columnList="observed_on"),@Index(name="idx_demand_signal_role",columnList="industry, job_role")})
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class DemandSignal extends BaseEntity{
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=200) private String title;
 @Column(nullable=false,length=100) private String industry;
 @Column(name="job_role",nullable=false,length=100) private String jobRole;
 @Column(nullable=false,length=1000) private String skills;
 @Column(name="demand_score",nullable=false) private int demandScore;
 @Column(name="observed_on",nullable=false) private LocalDate observedOn;
 @Enumerated(EnumType.STRING) @Column(name="source_type",nullable=false,length=20) private DemandSignalSource sourceType;
 @Column(name="source_name",length=200) private String sourceName;
 @Column(name="source_url",length=500) private String sourceUrl;
 @Column(columnDefinition="TEXT") private String notes;
 @Builder private DemandSignal(String title,String industry,String jobRole,String skills,int demandScore,LocalDate observedOn,DemandSignalSource sourceType,String sourceName,String sourceUrl,String notes){this.title=title;this.industry=industry;this.jobRole=jobRole;this.skills=skills;this.demandScore=Math.max(0,Math.min(100,demandScore));this.observedOn=observedOn;this.sourceType=sourceType;this.sourceName=sourceName;this.sourceUrl=sourceUrl;this.notes=notes;}
}
