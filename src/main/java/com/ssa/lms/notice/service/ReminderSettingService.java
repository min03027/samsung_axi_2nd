package com.ssa.lms.notice.service;

import com.ssa.lms.notice.entity.ReminderSetting;
import com.ssa.lms.notice.repository.ReminderSettingRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리마인드 알림 설정 조회·저장.
 *
 * <p><b>없으면 만들어서 준다.</b> 설정이 없다고 알림 기능이 멈추면 안 된다.
 * 최초 기동이나 DB 초기화 뒤에도 기본값(24h/1h/3일)으로 그냥 동작해야 한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReminderSettingService {

    private static final Logger log = LoggerFactory.getLogger(ReminderSettingService.class);

    private final ReminderSettingRepository repository;

    /** 현재 설정. 없으면 기본값으로 만들어 저장한 뒤 돌려준다. */
    @Transactional
    public ReminderSetting current() {
        return repository.findById(ReminderSetting.SINGLETON_ID)
                .orElseGet(() -> {
                    log.info("[알림] 리마인드 설정이 없어 기본값으로 생성한다 (24h/1h/3일)");
                    return repository.save(ReminderSetting.createDefault());
                });
    }

    /**
     * 관리자 저장.
     *
     * <p>범위를 벗어나거나 순서가 뒤집힌 값은 엔티티가 바로잡는다.
     * 무엇으로 저장됐는지 로그로 남긴다 — 넣은 값과 다르게 저장되면 관리자가 혼란스럽다.</p>
     */
    @Transactional
    public ReminderSetting save(int firstHours, int secondHours, int overdueDays,
                                boolean assignment, boolean exam, boolean survey, boolean enabled) {
        return save(firstHours, secondHours, overdueDays, assignment, exam, survey, true, enabled);
    }

    @Transactional
    public ReminderSetting save(int firstHours, int secondHours, int overdueDays,
                                boolean assignment, boolean exam, boolean survey, boolean lesson,
                                boolean enabled) {
        ReminderSetting s = current();
        s.update(firstHours, secondHours, overdueDays, assignment, exam, survey, lesson, enabled);
        log.info("[알림] 리마인드 설정 변경 — {}시간 전 / {}시간 전 / 마감 후 {}일, "
                        + "과제 {} 시험 {} 설문 {}, 전체 {}",
                s.getFirstNoticeHours(), s.getSecondNoticeHours(), s.getOverdueDays(),
                onOff(s.isAssignmentEnabled()), onOff(s.isExamEnabled()),
                onOff(s.isSurveyEnabled()), onOff(s.isEnabled()));
        return s;
    }

    private String onOff(boolean v) {
        return v ? "켬" : "끔";
    }
}
