package com.ssa.lms.course.template;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.content.entity.Content;
import com.ssa.lms.content.repository.ContentRepository;
import com.ssa.lms.course.entity.*;
import com.ssa.lms.course.repository.*;
import com.ssa.lms.user.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class CourseTemplateService {
 private final CourseTemplateRepository templateRepository; private final CourseTemplateVersionRepository versionRepository; private final CourseTemplateLinkRepository linkRepository;
 private final CourseRepository courseRepository; private final CourseInstructorRepository instructorRepository; private final SubjectRepository subjectRepository; private final SessionRepository sessionRepository; private final ContentRepository contentRepository; private final ObjectMapper objectMapper;

 public List<CourseTemplateView> list(LoginUser actor){return templateRepository.findAllByOrderByUpdatedAtDescIdDesc().stream().filter(t->canAccess(t.getSourceCourse(),actor)).map(t->CourseTemplateView.of(t,linkRepository.countByTemplateId(t.getId()))).toList();}
 public CourseTemplateView view(Long id,LoginUser actor){CourseTemplate t=get(id);assertCourseAccess(t.getSourceCourse(),actor);return CourseTemplateView.of(t,linkRepository.countByTemplateId(id));}
 public List<CourseTemplateLinkView> links(Long id,LoginUser actor){CourseTemplate t=get(id);assertCourseAccess(t.getSourceCourse(),actor);return linkRepository.findByTemplateIdOrderByIdAsc(id).stream().map(CourseTemplateLinkView::of).toList();}
 public List<CourseTemplateVersion> versions(Long id,LoginUser actor){CourseTemplate t=get(id);assertCourseAccess(t.getSourceCourse(),actor);return versionRepository.findByTemplateIdOrderByVersionNoDesc(id);}
 public List<Course> courseOptions(LoginUser user){List<Course> all=courseRepository.findAllByOrderByStartDateDesc();if(user.getRole()==Role.ADMIN)return all;Set<Long> ids=new HashSet<>(instructorRepository.findCourseIdsByInstructorId(user.getId()));return all.stream().filter(c->ids.contains(c.getId())).toList();}

 @Transactional public Long create(CourseTemplateForm form,LoginUser actor){Course source=courseRepository.findById(form.getSourceCourseId()).orElseThrow();assertCourseAccess(source,actor);String json=encode(snapshot(source));CourseTemplate t=templateRepository.save(CourseTemplate.builder().sourceCourse(source).name(form.getName().trim()).description(trim(form.getDescription())).snapshotJson(json).build());versionRepository.save(CourseTemplateVersion.builder().template(t).versionNo(1).snapshotJson(json).changeSummary(summary(form.getChangeSummary(),"과정 템플릿 최초 등록")).syncedCourseCount(0).build());return t.getId();}

 @Transactional public int publish(Long id,String changeSummary,LoginUser actor){CourseTemplate t=get(id);assertCourseAccess(t.getSourceCourse(),actor);String json=encode(snapshot(t.getSourceCourse()));int version=t.publish(t.getName(),t.getDescription(),json);int synced=0;for(CourseTemplateLink link:linkRepository.findByTemplateIdAndAutoSyncSafeTrueOrderByIdAsc(id)){if(link.getTargetCourse().getStatus()!=CourseStatus.DRAFT)continue;applySafe(t,link.getTargetCourse());link.synced(version);synced++;}versionRepository.save(CourseTemplateVersion.builder().template(t).versionNo(version).snapshotJson(json).changeSummary(summary(changeSummary,"원본 과정 구조 업데이트")).syncedCourseCount(synced).build());return synced;}

 @Transactional public void deploy(Long id,CourseTemplateDeployForm form,LoginUser actor){CourseTemplate t=get(id);Course target=courseRepository.findById(form.getTargetCourseId()).orElseThrow();assertCourseAccess(t.getSourceCourse(),actor);assertCourseAccess(target,actor);if(target.getId().equals(t.getSourceCourse().getId()))throw new IllegalArgumentException("원본 과정에는 템플릿을 배치할 수 없습니다.");if(target.getStatus()!=CourseStatus.DRAFT)throw new IllegalStateException("학습 기록 보호를 위해 작성중(DRAFT) 과정에만 템플릿을 적용할 수 있습니다.");if(linkRepository.findByTemplateIdAndTargetCourseId(id,target.getId()).isPresent())throw new IllegalStateException("이미 연결된 과정입니다.");applySafe(t,target);linkRepository.save(CourseTemplateLink.builder().template(t).targetCourse(target).autoSyncSafe(form.getAutoSyncSafe()).build());}

 @Transactional public void sync(Long templateId,Long linkId,LoginUser actor){CourseTemplate t=get(templateId);CourseTemplateLink link=linkRepository.findById(linkId).orElseThrow();if(!link.getTemplate().getId().equals(templateId))throw new IllegalArgumentException("템플릿 연결이 일치하지 않습니다.");assertCourseAccess(link.getTargetCourse(),actor);applySafe(t,link.getTargetCourse());link.synced(t.getCurrentVersion());}

 private void applySafe(CourseTemplate template,Course target){if(target.getStatus()!=CourseStatus.DRAFT)throw new IllegalStateException("진행 중 과정은 자동 변경하지 않습니다. 작성중 과정에서만 동기화하세요.");CourseTemplateSnapshot snap=decode(template.getSnapshotJson());Map<Integer,Subject> subjects=subjectRepository.findByCourseIdOrderByOrderNo(target.getId()).stream().collect(Collectors.toMap(Subject::getOrderNo,Function.identity(),(a,b)->a,LinkedHashMap::new));Map<String,Session> sessions=new HashMap<>();for(CourseTemplateSnapshot.SubjectSnapshot ss:snap.subjects()){Subject subject=subjects.get(ss.orderNo());if(subject==null){subject=Subject.builder().name(ss.name()).description(ss.description()).orderNo(ss.orderNo()).build();target.addSubject(subject);subject=subjectRepository.save(subject);subjects.put(ss.orderNo(),subject);}else{subject.update(ss.name(),ss.description(),ss.orderNo());}Map<Integer,Session> existing=sessionRepository.findBySubjectIdOrderBySeq(subject.getId()).stream().collect(Collectors.toMap(Session::getSeq,Function.identity()));for(CourseTemplateSnapshot.SessionSnapshot se:ss.sessions()){Session session=existing.get(se.seq());if(session==null){session=Session.builder().seq(se.seq()).name(se.name()).lessonDate(se.lessonDate()).learningMinutes(se.learningMinutes()).build();subject.addSession(session);session=sessionRepository.save(session);}else session.update(se.seq(),se.name(),se.lessonDate(),se.learningMinutes());sessions.put(ss.orderNo()+":"+se.seq(),session);}}
  List<Content> current=contentRepository.findByCourseIdOrderByOrderNoAscIdAsc(target.getId());for(CourseTemplateSnapshot.ContentSnapshot cs:snap.contents()){Session targetSession=cs.subjectOrder()==null?null:sessions.get(cs.subjectOrder()+":"+cs.sessionSeq());Content found=current.stream().filter(c->same(c.getSession(),targetSession)&&c.getOrderNo()==cs.orderNo()).findFirst().orElse(null);if(found==null){found=contentRepository.save(Content.builder().course(target).session(targetSession).type(cs.type()).title(cs.title()).description(cs.description()).fileUrl(cs.fileUrl()).originalFileName(cs.originalFileName()).fileSize(cs.fileSize()).mimeType(cs.mimeType()).durationSeconds(cs.durationSeconds()).pageCount(cs.pageCount()).orderNo(cs.orderNo()).required(cs.required()).status(cs.status()).build());current.add(found);}else{found.syncFromLibrary(cs.type(),cs.title(),cs.description(),cs.fileUrl(),cs.originalFileName(),cs.fileSize(),cs.mimeType(),cs.durationSeconds(),cs.pageCount());found.update(targetSession,cs.title(),cs.description(),cs.durationSeconds(),cs.pageCount(),cs.orderNo(),cs.required(),cs.status());}}
 }

 private CourseTemplateSnapshot snapshot(Course course){List<CourseTemplateSnapshot.SubjectSnapshot> subjects=subjectRepository.findByCourseIdOrderByOrderNo(course.getId()).stream().map(s->new CourseTemplateSnapshot.SubjectSnapshot(s.getOrderNo(),s.getName(),s.getDescription(),sessionRepository.findBySubjectIdOrderBySeq(s.getId()).stream().map(se->new CourseTemplateSnapshot.SessionSnapshot(se.getSeq(),se.getName(),se.getLessonDate(),se.getLearningMinutes())).toList())).toList();List<CourseTemplateSnapshot.ContentSnapshot> contents=contentRepository.findByCourseIdOrderByOrderNoAscIdAsc(course.getId()).stream().map(c->{Integer so=c.getSession()==null?null:c.getSession().getSubject().getOrderNo();Integer seq=c.getSession()==null?null:c.getSession().getSeq();return new CourseTemplateSnapshot.ContentSnapshot(so,seq,c.getType(),c.getTitle(),c.getDescription(),c.getFileUrl(),c.getOriginalFileName(),c.getFileSize(),c.getMimeType(),c.getDurationSeconds(),c.getPageCount(),c.getOrderNo(),c.isRequired(),c.getStatus());}).toList();return new CourseTemplateSnapshot(subjects,contents);}
 private boolean same(Session a,Session b){return a==null?b==null:b!=null&&a.getId().equals(b.getId());}
 private String encode(CourseTemplateSnapshot s){try{return objectMapper.writeValueAsString(s);}catch(JsonProcessingException e){throw new IllegalStateException("과정 템플릿 직렬화 실패",e);}}
 private CourseTemplateSnapshot decode(String json){try{return objectMapper.readValue(json,CourseTemplateSnapshot.class);}catch(JsonProcessingException e){throw new IllegalStateException("과정 템플릿 해석 실패",e);}}
 private CourseTemplate get(Long id){return templateRepository.findById(id).orElseThrow(()->new IllegalArgumentException("과정 템플릿을 찾을 수 없습니다: "+id));}
 private void assertCourseAccess(Course c,LoginUser a){if(a.getRole()==Role.ADMIN)return;if(a.getRole()!=Role.INSTRUCTOR||!instructorRepository.existsByCourseIdAndInstructorId(c.getId(),a.getId()))throw new AccessDeniedException("담당 과정만 템플릿으로 관리할 수 있습니다.");}
 private boolean canAccess(Course c,LoginUser a){return a.getRole()==Role.ADMIN||(a.getRole()==Role.INSTRUCTOR&&instructorRepository.existsByCourseIdAndInstructorId(c.getId(),a.getId()));}
 private String trim(String v){return v==null||v.isBlank()?null:v.trim();}private String summary(String v,String d){return v==null||v.isBlank()?d:v.trim();}
}
