package com.ssa.lms.demand;
public enum DemandSignalSource { MANUAL("수동 등록"), CSV("CSV 업로드"), API("외부 API"); private final String label; DemandSignalSource(String label){this.label=label;} public String getLabel(){return label;} }
