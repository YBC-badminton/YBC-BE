package com.ybc.ybc.contents.service;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.service.MemberService;
import com.ybc.ybc.contents.domain.PrivacyPolicy;
import com.ybc.ybc.contents.dto.request.PrivacyPolicyUpdateRequest;
import com.ybc.ybc.contents.dto.response.PrivacyPolicyResponse;
import com.ybc.ybc.contents.repository.PrivacyPolicyRepository;
import com.ybc.ybc.global.exception.YbcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivacyPolicyServiceTest {
    private static final Long ADMIN_MEMBER_ID = 1L;
    private static final Long NON_ADMIN_MEMBER_ID = 2L;

    @Mock
    private MemberService memberService;

    @Mock
    private PrivacyPolicyRepository privacyPolicyRepository;

    @InjectMocks
    private PrivacyPolicyService privacyPolicyService;

    @BeforeEach
    void setUp() {
        lenient().when(memberService.getMemberById(ADMIN_MEMBER_ID)).thenReturn(adminMember());
        lenient().when(memberService.getMemberById(NON_ADMIN_MEMBER_ID)).thenReturn(nonAdminMember());
    }

    @Test
    @DisplayName("개인정보 처리방침이 없으면 새로 생성한다")
    void updatePrivacyPolicyCreatesNewPrivacyPolicyWhenItDoesNotExist() {
        PrivacyPolicyUpdateRequest request = new PrivacyPolicyUpdateRequest("개인정보 처리방침 내용입니다.");

        when(privacyPolicyRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(privacyPolicyRepository.saveAndFlush(any(PrivacyPolicy.class)))
                .thenAnswer(invocation -> {
                    PrivacyPolicy privacyPolicy = invocation.getArgument(0);
                    ReflectionTestUtils.setField(privacyPolicy, "id", 1L);
                    ReflectionTestUtils.setField(privacyPolicy, "createdAt", LocalDateTime.of(2026, 7, 9, 10, 0));
                    ReflectionTestUtils.setField(privacyPolicy, "updatedAt", LocalDateTime.of(2026, 7, 9, 10, 0));
                    return privacyPolicy;
                });

        PrivacyPolicyResponse response = privacyPolicyService.updatePrivacyPolicy(ADMIN_MEMBER_ID, request);

        assertThat(response.content()).isEqualTo("개인정보 처리방침 내용입니다.");
        assertThat(response.updatedAt()).isEqualTo(LocalDate.of(2026, 7, 9));

        ArgumentCaptor<PrivacyPolicy> captor = ArgumentCaptor.forClass(PrivacyPolicy.class);
        verify(privacyPolicyRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("개인정보 처리방침 내용입니다.");
    }

    @Test
    @DisplayName("기존 개인정보 처리방침이 있으면 내용을 수정한다")
    void updatePrivacyPolicyUpdatesExistingPrivacyPolicy() {
        PrivacyPolicy existingPrivacyPolicy = PrivacyPolicy.create("기존 내용입니다.");
        ReflectionTestUtils.setField(existingPrivacyPolicy, "id", 1L);
        ReflectionTestUtils.setField(existingPrivacyPolicy, "createdAt", LocalDateTime.of(2026, 7, 1, 9, 0));
        ReflectionTestUtils.setField(existingPrivacyPolicy, "updatedAt", LocalDateTime.of(2026, 7, 1, 9, 0));

        when(privacyPolicyRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existingPrivacyPolicy));
        when(privacyPolicyRepository.saveAndFlush(existingPrivacyPolicy))
                .thenAnswer(invocation -> {
                    ReflectionTestUtils.setField(existingPrivacyPolicy, "updatedAt", LocalDateTime.of(2026, 7, 10, 12, 0));
                    return existingPrivacyPolicy;
                });

        PrivacyPolicyResponse response = privacyPolicyService.updatePrivacyPolicy(
                ADMIN_MEMBER_ID,
                new PrivacyPolicyUpdateRequest("수정된 개인정보 처리방침 내용입니다.")
        );

        assertThat(existingPrivacyPolicy.getContent()).isEqualTo("수정된 개인정보 처리방침 내용입니다.");
        assertThat(response.content()).isEqualTo("수정된 개인정보 처리방침 내용입니다.");
        assertThat(response.updatedAt()).isEqualTo(LocalDate.of(2026, 7, 10));
    }

    @Test
    @DisplayName("개인정보 처리방침을 조회할 수 있다")
    void getPrivacyPolicyReturnsPrivacyPolicy() {
        PrivacyPolicy privacyPolicy = PrivacyPolicy.create("개인정보 처리방침 내용입니다.");
        ReflectionTestUtils.setField(privacyPolicy, "id", 1L);
        ReflectionTestUtils.setField(privacyPolicy, "createdAt", LocalDateTime.of(2026, 7, 9, 10, 0));
        ReflectionTestUtils.setField(privacyPolicy, "updatedAt", LocalDateTime.of(2026, 7, 9, 10, 0));

        when(privacyPolicyRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(privacyPolicy));

        PrivacyPolicyResponse response = privacyPolicyService.getPrivacyPolicy();

        assertThat(response.content()).isEqualTo("개인정보 처리방침 내용입니다.");
        assertThat(response.updatedAt()).isEqualTo(LocalDate.of(2026, 7, 9));
    }

    @Test
    @DisplayName("개인정보 처리방침이 없으면 조회 시 예외가 발생한다")
    void getPrivacyPolicyThrowsWhenPrivacyPolicyDoesNotExist() {
        when(privacyPolicyRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> privacyPolicyService.getPrivacyPolicy())
                .isInstanceOf(YbcException.class)
                .hasMessage("개인정보 처리방침을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("관리자가 아니면 개인정보 처리방침을 수정할 수 없다")
    void updatePrivacyPolicyThrowsWhenMemberIsNotAdmin() {
        assertThatThrownBy(() -> privacyPolicyService.updatePrivacyPolicy(
                NON_ADMIN_MEMBER_ID,
                new PrivacyPolicyUpdateRequest("개인정보 처리방침 내용입니다.")
        ))
                .isInstanceOf(YbcException.class)
                .hasMessage("접근 권한이 없는 회원입니다.");

        verify(privacyPolicyRepository, never()).saveAndFlush(any(PrivacyPolicy.class));
    }

    @Test
    @DisplayName("내용이 없으면 개인정보 처리방침을 수정할 수 없다")
    void updatePrivacyPolicyThrowsWhenContentIsMissing() {
        assertThatThrownBy(() -> privacyPolicyService.updatePrivacyPolicy(
                ADMIN_MEMBER_ID,
                new PrivacyPolicyUpdateRequest(null)
        ))
                .isInstanceOf(YbcException.class)
                .hasMessage("필수 파라미터 누락");

        verify(privacyPolicyRepository, never()).saveAndFlush(any(PrivacyPolicy.class));
    }

    private Member adminMember() {
        return Member.createSocialMember(
                SocialProvider.KAKAO,
                "123456789",
                "관리자",
                "admin@example.com",
                true
        );
    }

    private Member nonAdminMember() {
        return Member.createSocialMember(
                SocialProvider.KAKAO,
                "987654321",
                "일반회원",
                "user@example.com",
                false
        );
    }
}
