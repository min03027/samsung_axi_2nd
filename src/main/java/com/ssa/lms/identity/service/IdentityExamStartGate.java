package com.ssa.lms.identity.service;

import com.ssa.lms.exam.entity.ExamAttempt;
import com.ssa.lms.exam.service.ExamAttemptService;
import com.ssa.lms.exam.service.ExamTakeException;
import com.ssa.lms.identity.entity.ExamIdentitySession;
import com.ssa.lms.identity.entity.IdentityAccessDeniedException;
import com.ssa.lms.identity.entity.IdentitySessionStateException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 시험 시작 서버 게이트 구현 (LXP-015 / LXP-018).
 *
 * <p>exam 모듈의 {@link ExamAttemptService.ExamStartGate} 포트를 identity 쪽에서 채운다.
 * exam 이 identity 를 직접 의존하지 않게 하려는 것이고, 이 빈이 없으면 기존 흐름 그대로 돈다.</p>
 *
 * <p><b>화면이 보낸 값은 아무것도 믿지 않는다.</b> precheckSessionId 조차 소유자·시험 일치를
 * 다시 확인하고, 승인 상태·유효시간·웹캠 신선도는 서버 데이터로만 판정한다.</p>
 */
@Component
@RequiredArgsConstructor
public class IdentityExamStartGate implements ExamAttemptService.ExamStartGate {

    private final ExamIdentityService identityService;

    @Override
    public void check(Long examId, Long userId, Long precheckSessionId) {
        /* 사전점검을 거치지 않은 시험(비감독·기존 비밀번호 흐름)은 통과시킨다.
           여기서 무조건 막으면 기존 시험이 전부 회귀한다. */
        if (!identityService.requiresPrecheck(examId)) {
            return;
        }
        /* precheckSessionId 누락도 차단한다 (지적 4).
           "없으면 서버가 알아서 찾아 준다" 로 두면 사전점검 화면을 거치지 않은 요청이
           그대로 통과할 여지가 생긴다. 대상 시험이면 반드시 명시해야 한다. */
        if (precheckSessionId == null) {
            throw ExamTakeException.identityRequired(
                    "사전점검을 거쳐야 시작할 수 있습니다. 사전점검 화면에서 시작해 주세요.");
        }
        try {
            ExamIdentitySession session = identityService.requireApproved(examId, userId);
            /* 승인된 최신 세션과 전달된 세션 ID 가 정확히 일치해야 한다.
               다르면 남의 세션 id 를 끼워 넣었거나 오래된 세션으로 들어오려는 것이다. */
            if (!session.getId().equals(precheckSessionId)) {
                throw ExamTakeException.identityRequired("사전점검 세션이 일치하지 않습니다.");
            }
        } catch (IdentityAccessDeniedException e) {
            throw ExamTakeException.identityRequired(e.getMessage());
        } catch (IdentitySessionStateException e) {
            throw ExamTakeException.identityRequired(e.getMessage());
        }
    }

    /**
     * 검증을 통과한 <b>그 세션</b>에만 attempt 를 연결한다.
     *
     * <p>이전 구현은 전달받은 id 를 무시하고 (exam,user) 최신 세션을 다시 찾았다.
     * 그 사이 새 세션이 생기면 <b>승인되지 않은 세션</b>에 attempt 가 붙는다.</p>
     */
    @Override
    public void linkAttempt(Long precheckSessionId, ExamAttempt attempt) {
        if (attempt == null || precheckSessionId == null) {
            return;
        }
        identityService.linkAttemptToSession(precheckSessionId, attempt);
    }
}
