package com.ssa.lms.demand;
public enum DemandRecommendationStatus { PENDING("검토 대기"), APPLIED("과정 반영 결정"), DISMISSED("보류"); private final String label; DemandRecommendationStatus(String label){this.label=label;} public String getLabel(){return label;} }
