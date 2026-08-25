package com.ssa.lms.demand;
import java.time.LocalDate;
public record DemandSignalView(Long id,String title,String industry,String jobRole,String skills,int demandScore,LocalDate observedOn,String sourceLabel,long recommendationCount){public static DemandSignalView of(DemandSignal s,long count){return new DemandSignalView(s.getId(),s.getTitle(),s.getIndustry(),s.getJobRole(),s.getSkills(),s.getDemandScore(),s.getObservedOn(),s.getSourceType().getLabel(),count);}}
