package com.ssa.lms.user.repository;

import com.ssa.lms.user.entity.Role;
import com.ssa.lms.user.entity.User;
import com.ssa.lms.user.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    long countByRole(Role role);

    /** 승인 대기 목록 등 상태별 조회 — 오래된 가입 순(FIFO)으로 정렬 */
    List<User> findByStatusOrderByCreatedAtAsc(UserStatus status);

    long countByStatus(UserStatus status);

    /** 역할별 목록(강사/훈련생/관리자 관리) — 이름 가나다 순 */
    List<User> findByRoleOrderByNameAsc(Role role);

    List<User> findByRoleAndStatusOrderByNameAsc(Role role, UserStatus status);

    /** 역할·상태별 계정 수 — "마지막 활성 관리자" 비활성화 방지에 사용. */
    long countByRoleAndStatus(Role role, UserStatus status);

    /** 프로필 이미지가 없는 계정 — 기본 아바타 백필(ProfileImageBackfillRunner)에 사용. */
    List<User> findByProfileImageUrlIsNull();
}
