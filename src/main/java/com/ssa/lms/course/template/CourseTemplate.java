package com.ssa.lms.course.template;

import com.ssa.lms.common.entity.BaseEntity;
import com.ssa.lms.course.entity.Course;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name="course_template", indexes=@Index(name="idx_course_template_status",columnList="status, updated_at"))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class CourseTemplate extends BaseEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="source_course_id",nullable=false) private Course sourceCourse;
 @Column(nullable=false,length=200) private String name;
 @Column(columnDefinition="TEXT") private String description;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private CourseTemplateStatus status;
 @Column(name="current_version",nullable=false) private int currentVersion;
 @Column(name="snapshot_json",nullable=false,columnDefinition="TEXT") private String snapshotJson;
 @Version @Column(name="row_version",nullable=false) private long rowVersion;
 @Builder private CourseTemplate(Course sourceCourse,String name,String description,String snapshotJson){this.sourceCourse=sourceCourse;this.name=name;this.description=description;this.snapshotJson=snapshotJson;this.status=CourseTemplateStatus.PUBLISHED;this.currentVersion=1;}
 public int publish(String name,String description,String snapshotJson){this.name=name;this.description=description;this.snapshotJson=snapshotJson;this.status=CourseTemplateStatus.PUBLISHED;return ++this.currentVersion;}
 public void archive(){this.status=CourseTemplateStatus.ARCHIVED;}
}
