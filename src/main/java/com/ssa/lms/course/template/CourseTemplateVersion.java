package com.ssa.lms.course.template;
import com.ssa.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="course_template_version",uniqueConstraints=@UniqueConstraint(name="uk_course_template_version",columnNames={"template_id","version_no"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class CourseTemplateVersion extends BaseEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="template_id",nullable=false) private CourseTemplate template;
 @Column(name="version_no",nullable=false) private int versionNo;
 @Column(name="snapshot_json",nullable=false,columnDefinition="TEXT") private String snapshotJson;
 @Column(name="change_summary",nullable=false,columnDefinition="TEXT") private String changeSummary;
 @Column(name="synced_course_count",nullable=false) private int syncedCourseCount;
 @Builder private CourseTemplateVersion(CourseTemplate template,int versionNo,String snapshotJson,String changeSummary,int syncedCourseCount){this.template=template;this.versionNo=versionNo;this.snapshotJson=snapshotJson;this.changeSummary=changeSummary;this.syncedCourseCount=syncedCourseCount;}
}
