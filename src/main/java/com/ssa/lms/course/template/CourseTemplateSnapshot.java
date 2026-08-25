package com.ssa.lms.course.template;
import com.ssa.lms.content.entity.*;
import java.time.LocalDate;
import java.util.List;
public record CourseTemplateSnapshot(List<SubjectSnapshot> subjects,List<ContentSnapshot> contents){
 public record SubjectSnapshot(int orderNo,String name,String description,List<SessionSnapshot> sessions){}
 public record SessionSnapshot(int seq,String name,LocalDate lessonDate,Integer learningMinutes){}
 public record ContentSnapshot(Integer subjectOrder,Integer sessionSeq,ContentType type,String title,String description,String fileUrl,String originalFileName,Long fileSize,String mimeType,Integer durationSeconds,Integer pageCount,int orderNo,boolean required,ContentStatus status){}
}
