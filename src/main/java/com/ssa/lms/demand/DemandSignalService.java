package com.ssa.lms.demand;

import com.ssa.lms.content.entity.ContentLibraryItem;
import com.ssa.lms.content.entity.ContentLibraryStatus;
import com.ssa.lms.content.repository.ContentLibraryItemRepository;
import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.user.entity.User;
import com.ssa.lms.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class DemandSignalService{
 private final DemandSignalRepository signalRepository;private final DemandRecommendationRepository recommendationRepository;private final CourseRepository courseRepository;private final ContentLibraryItemRepository libraryRepository;private final UserRepository userRepository;
 public List<DemandSignalView> list(){return signalRepository.findAllByOrderByObservedOnDescIdDesc().stream().map(s->DemandSignalView.of(s,recommendationRepository.findBySignalIdOrderByMatchScoreDesc(s.getId()).size())).toList();}
 public DemandSignalView view(Long id){DemandSignal s=get(id);return DemandSignalView.of(s,recommendationRepository.findBySignalIdOrderByMatchScoreDesc(id).size());}
 public List<DemandRecommendationView> recommendations(Long signalId){return recommendationRepository.findBySignalIdOrderByMatchScoreDesc(signalId).stream().map(DemandRecommendationView::of).toList();}
 public long pendingCount(){return recommendationRepository.countByStatus(DemandRecommendationStatus.PENDING);}
 @Transactional public Long create(DemandSignalForm f){DemandSignal s=save(f,DemandSignalSource.MANUAL);generate(s);return s.getId();}
 @Transactional public int importCsv(MultipartFile file){if(file==null||file.isEmpty())throw new IllegalArgumentException("CSV 파일을 선택하세요.");try{String text=new String(file.getBytes(),StandardCharsets.UTF_8).replace("\uFEFF","");String[] lines=text.split("\\R");int count=0;for(int i=1;i<lines.length;i++){if(lines[i].isBlank())continue;String[] c=lines[i].split(",",-1);if(c.length<6)throw new IllegalArgumentException((i+1)+"행의 열이 부족합니다.");DemandSignalForm f=new DemandSignalForm();f.setObservedOn(LocalDate.parse(c[0].trim()));f.setIndustry(c[1].trim());f.setJobRole(c[2].trim());f.setSkills(c[3].trim().replace('|',','));f.setDemandScore(Integer.parseInt(c[4].trim()));f.setTitle(c[5].trim());if(c.length>6)f.setSourceName(c[6].trim());if(c.length>7)f.setSourceUrl(c[7].trim());DemandSignal s=save(f,DemandSignalSource.CSV);generate(s);count++;}return count;}catch(IOException e){throw new IllegalArgumentException("CSV 파일을 읽을 수 없습니다.",e);}}
 @Transactional public void review(Long signalId,Long recommendationId,DemandRecommendationStatus status,String note,Long actorId){if(status==DemandRecommendationStatus.PENDING)throw new IllegalArgumentException("처리 상태를 선택하세요.");DemandRecommendation r=recommendationRepository.findById(recommendationId).orElseThrow();if(!r.getSignal().getId().equals(signalId))throw new IllegalArgumentException("수요 신호와 추천 항목이 일치하지 않습니다.");User actor=userRepository.findById(actorId).orElseThrow();r.review(status,trim(note),actor);}
 private DemandSignal save(DemandSignalForm f,DemandSignalSource source){return signalRepository.save(DemandSignal.builder().title(f.getTitle().trim()).industry(f.getIndustry().trim()).jobRole(f.getJobRole().trim()).skills(normalize(f.getSkills())).demandScore(f.getDemandScore()).observedOn(f.getObservedOn()).sourceType(source).sourceName(trim(f.getSourceName())).sourceUrl(trim(f.getSourceUrl())).notes(trim(f.getNotes())).build());}
 private void generate(DemandSignal signal){
  Set<String> skills=tokens(signal.getSkills());
  List<ContentLibraryItem> library=libraryRepository.findAllByOrderByUpdatedAtDescIdDesc().stream()
          .filter(i->i.getStatus()==ContentLibraryStatus.PUBLISHED).toList();
  for(Course course:courseRepository.findAllByOrderByStartDateDesc()){
   Set<String> haystack=tokens(String.join(",",safe(course.getCourseName()),safe(course.getCategory()),safe(course.getDescription())));
   Set<String> matched=new LinkedHashSet<>(skills);matched.retainAll(haystack);
   ContentLibraryItem suggested=library.stream()
           .max(Comparator.comparingInt(i->overlap(skills,libraryTokens(i))))
           .filter(i->overlap(skills,libraryTokens(i))>0).orElse(null);
   int contentScore=suggested==null?0:overlap(skills,libraryTokens(suggested));
   if(matched.isEmpty()&&contentScore==0)continue;
   int score=Math.min(100,matched.size()*25+contentScore*15+signal.getDemandScore()/4);
   recommendationRepository.findBySignalIdAndCourseId(signal.getId(),course.getId())
           .orElseGet(()->recommendationRepository.save(DemandRecommendation.builder()
                   .signal(signal).course(course).suggestedLibraryItem(suggested).matchScore(score)
                   .matchedKeywords(matched.isEmpty()?signal.getSkills():String.join(", ",matched)).build()));
  }
 }
 private Set<String> libraryTokens(ContentLibraryItem item){return tokens(String.join(",",safe(item.getIndustryTags()),safe(item.getTitle()),safe(item.getDescription())));}
 private DemandSignal get(Long id){return signalRepository.findById(id).orElseThrow(()->new IllegalArgumentException("수요 신호를 찾을 수 없습니다: "+id));}
 private Set<String> tokens(String value){Set<String> set=new LinkedHashSet<>();if(value==null)return set;for(String s:value.toLowerCase(Locale.ROOT).split("[,|/\\s]+")){String t=s.trim();if(t.length()>=2)set.add(t);}return set;}private int overlap(Set<String>a,Set<String>b){Set<String>x=new HashSet<>(a);x.retainAll(b);return x.size();}private String normalize(String v){return String.join(", ",tokens(v));}private String safe(String v){return v==null?"":v;}private String trim(String v){return v==null||v.isBlank()?null:v.trim();}
}
