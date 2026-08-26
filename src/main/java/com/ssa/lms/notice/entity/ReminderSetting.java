package com.ssa.lms.notice.entity;

import com.ssa.lms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리마인드 알림 설정 — <b>관리자가 화면에서 바꾼다.</b>
 *
 * <p>예전에는 "마감 24시간 전 / 1시간 전 / 마감 후 3일" 이 코드에 박혀 있었다.
 * 기관마다 운영 방식이 다른데 바꾸려면 코드를 고쳐 재배포해야 했다.</p>
 *
 * <p><b>한 행만 쓴다</b>({@link #SINGLETON_ID}). 설정은 기관 전체에 하나뿐이고,
 * 여러 행이 생기면 어느 것이 적용됐는지 알 수 없게 된다.</p>
 *
 * <p><b>0 은 "끔"이 아니라 "즉시"다.</b> 헷갈리기 쉬워서 켜고 끄는 것은 별도 플래그로 둔다.
 * 시간만 0으로 만들어 끄려 하면, 마감 시각에 알림이 쏟아진다.</p>
 */
@Entity
@Table(name = "reminder_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReminderSetting extends BaseEntity {

    /** 설정은 하나뿐이다. 항상 이 id 를 쓴다. */
    public static final long SINGLETON_ID = 1L;

    /* 관리자가 넣을 수 있는 범위. 벗어난 값은 저장 전에 막는다 —
       음수 시간은 미래에 알림을 보내는 셈이 되고, 지나치게 크면 강의 시작 전에 알림이 간다. */
    public static final int MIN_HOURS = 1;
    public static final int MAX_HOURS = 720;      // 30일
    public static final int MAX_OVERDUE_DAYS = 30;

    @Id
    @Column(name = "id")
    private Long id = SINGLETON_ID;

    /** 1차 알림 — 마감 몇 시간 전. 기본 24시간. */
    @Column(name = "first_notice_hours", nullable = false)
    private int firstNoticeHours = 24;

    /** 2차 알림 — 마감 몇 시간 전. 기본 1시간. */
    @Column(name = "second_notice_hours", nullable = false)
    private int secondNoticeHours = 1;

    /** 마감 후 독려를 며칠까지 보낼지. 기본 3일. */
    @Column(name = "overdue_days", nullable = false)
    private int overdueDays = 3;

    /* 대상별 on/off — 기관에 따라 설문은 안 보내고 싶을 수 있다 */
    @Column(name = "assignment_enabled", nullable = false)
    private boolean assignmentEnabled = true;

    @Column(name = "exam_enabled", nullable = false)
    private boolean examEnabled = true;

    @Column(name = "survey_enabled", nullable = false)
    private boolean surveyEnabled = true;

    @Column(name = "lesson_enabled", nullable = false, columnDefinition = "boolean default true")
    private boolean lessonEnabled = true;

    /** 리마인드 전체 스위치. 시간을 0으로 만들어 끄려 하면 마감 시각에 쏟아진다. */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    public static ReminderSetting createDefault() {
        return new ReminderSetting();
    }

    /**
     * 관리자 화면에서 저장할 때.
     *
     * <p>범위를 벗어난 값은 <b>가장 가까운 허용값으로 맞춘다.</b> 저장을 거부하면
     * 관리자가 왜 안 되는지 모른 채 헤매고, 그대로 받으면 알림이 이상하게 나간다.</p>
     */
    public void update(int firstHours, int secondHours, int overdueDays,
                       boolean assignment, boolean exam, boolean survey, boolean enabled) {
        update(firstHours, secondHours, overdueDays, assignment, exam, survey, true, enabled);
    }

    public void update(int firstHours, int secondHours, int overdueDays,
                       boolean assignment, boolean exam, boolean survey, boolean lesson, boolean enabled) {
        this.firstNoticeHours = clamp(firstHours, MIN_HOURS, MAX_HOURS);
        this.secondNoticeHours = clamp(secondHours, MIN_HOURS, MAX_HOURS);
        this.overdueDays = clamp(overdueDays, 1, MAX_OVERDUE_DAYS);

        /*
         * 2차가 1차보다 이르면 순서가 뒤집혀 "1시간 전" 알림이 "24시간 전"보다 먼저 간다.
         * 관리자가 두 값을 바꿔 넣는 실수를 조용히 바로잡는다.
         */
        if (this.secondNoticeHours > this.firstNoticeHours) {
            int tmp = this.firstNoticeHours;
            this.firstNoticeHours = this.secondNoticeHours;
            this.secondNoticeHours = tmp;
        }

        this.assignmentEnabled = assignment;
        this.examEnabled = exam;
        this.surveyEnabled = survey;
        this.lessonEnabled = lesson;
        this.enabled = enabled;
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
