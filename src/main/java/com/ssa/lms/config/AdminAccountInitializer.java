package com.ssa.lms.config;

import com.ssa.lms.user.entity.Role;
import com.ssa.lms.user.entity.User;
import com.ssa.lms.user.entity.UserStatus;
import com.ssa.lms.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * local 이외 프로필(dev/운영)용 초기 관리자 계정 부트스트랩 — b-audit-reply.md §3-3(6) 대응.
 *
 * <p>{@code LocalDataInitializer} 는 {@code @Profile("local")} 전용이라 PostgreSQL(dev)로 띄우면
 * 계정이 하나도 없는 빈 DB 로 시작해 로그인 자체가 불가능했다. 여기서는 <b>ADMIN 계정이 하나도
 * 없을 때만</b> 관리자 1개를 만든다 (데모 과정·훈련생 시드는 만들지 않음 — 운영 데이터는 화면에서 생성).</p>
 *
 * <p>비밀번호는 환경변수 {@code LMS_ADMIN_INIT_PASSWORD} 로 지정한다. 미지정 시 무작위 비밀번호를
 * 생성해 로그에 1회 출력하므로, 첫 로그인 후 반드시 변경할 것.</p>
 */
@Slf4j
@Component
@Profile("!local & !demo")
@Order(0)
@RequiredArgsConstructor
public class AdminAccountInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${lms.admin.init-password:}")
    private String initPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.countByRole(Role.ADMIN) > 0) {
            return;
        }

        String rawPassword = initPassword;
        boolean generated = rawPassword == null || rawPassword.isBlank();
        if (generated) {
            rawPassword = UUID.randomUUID().toString().substring(0, 12);
        }

        userRepository.save(User.builder()
                .loginId("admin").password(passwordEncoder.encode(rawPassword))
                .name("관리자").role(Role.ADMIN).status(UserStatus.ACTIVE)
                .privacyConsentAt(LocalDateTime.now()).thirdPartyConsentAt(LocalDateTime.now())
                .build());

        if (generated) {
            log.warn("[bootstrap] 초기 관리자 계정 생성 — id: admin, 임시 비밀번호: {} (즉시 변경 필수. "
                    + "고정하려면 env LMS_ADMIN_INIT_PASSWORD 설정)", rawPassword);
        } else {
            log.info("[bootstrap] 초기 관리자 계정 생성 — id: admin (비밀번호는 LMS_ADMIN_INIT_PASSWORD)");
        }
    }
}
