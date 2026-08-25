package com.ssa.lms.course.template;
import com.ssa.lms.common.entity.BaseEntity;
import com.ssa.lms.course.entity.Course;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="course_template_link",uniqueConstraints=@UniqueConstraint(name="uk_course_template_target",columnNames={"template_id","target_course_id"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class CourseTemplateLink extends BaseEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="template_id",nullable=false) private CourseTemplate template;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="target_course_id",nullable=false) private Course targetCourse;
 @Column(name="auto_sync_safe",nullable=false) private boolean autoSyncSafe;
 @Column(name="applied_version",nullable=false) private int appliedVersion;
 @Column(name="last_synced_at",nullable=false) private LocalDateTime lastSyncedAt;
 @Builder private CourseTemplateLink(CourseTemplate template,Course targetCourse,Boolean autoSyncSafe){this.template=template;this.targetCourse=targetCourse;this.autoSyncSafe=autoSyncSafe==null||autoSyncSafe;this.appliedVersion=template.getCurrentVersion();this.lastSyncedAt=LocalDateTime.now();}
 public void synced(int version){this.appliedVersion=version;this.lastSyncedAt=LocalDateTime.now();}
 public void changeAutoSync(boolean value){this.autoSyncSafe=value;}
}
