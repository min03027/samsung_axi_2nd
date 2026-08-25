package com.ssa.lms.identity.policy;

import com.ssa.lms.exam.entity.Exam;

/**
 * "이 시험이 QR 신분확인 사전점검 대상인가" 를 정하는 <b>유일한</b> 자리.
 *
 * <p><b>왜 클래스로 뽑았나</b><br>
 * 같은 판정이 {@code ExamAttemptService}(게이트)·{@code ExamIdentityService}·목록 DTO 세 곳에
 * 흩어져 있었다. 한 곳만 고치면 "목록에서는 사전점검으로 보내는데 게이트는 통과시키는" 식으로
 * 어긋난다. 공용 {@code Exam} 엔티티를 건드리지 않고 이 기능 전용 정책으로 분리한다.</p>
 *
 * <p><b>판정</b>: {@code proctorEnabled && requireIdentityVerification}<br>
 * 본인확인만 켜진 시험은 <b>대상이 아니다</b> — 기존 비밀번호 모달을 그대로 쓴다.</p>
 */
public final class PrecheckPolicy {

    private PrecheckPolicy() {
    }

    /** QR 신분확인 + 웹캠 사전점검을 거쳐야 하는 시험인가. */
    public static boolean requiresPrecheck(Exam exam) {
        return exam != null && exam.isProctorEnabled() && exam.isRequireIdentityVerification();
    }

    /**
     * 사전점검 대상은 아니지만 기존 비밀번호 본인인증은 필요한 시험인가.
     * 화면이 비밀번호 모달을 띄울지 판단할 때 쓴다.
     */
    public static boolean requiresPasswordVerification(Exam exam) {
        return exam != null && exam.isRequireIdentityVerification() && !requiresPrecheck(exam);
    }
}
