package com.ybc.ybc.contents.service;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.service.MemberService;
import com.ybc.ybc.contents.domain.HomeContent;
import com.ybc.ybc.contents.dto.request.HomeContentUpdateRequest;
import com.ybc.ybc.contents.dto.response.HomeContentResponse;
import com.ybc.ybc.contents.repository.HomeContentRepository;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.images.service.ManagedImageCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeContentServiceTest {
    private static final Long ADMIN_MEMBER_ID = 1L;
    private static final Long NON_ADMIN_MEMBER_ID = 2L;

    @Mock
    private MemberService memberService;

    @Mock
    private HomeContentRepository homeContentRepository;

    @Mock
    private ManagedImageCleanupService managedImageCleanupService;

    @InjectMocks
    private HomeContentService homeContentService;

    @BeforeEach
    void setUp() {
        lenient().when(memberService.getMemberById(ADMIN_MEMBER_ID)).thenReturn(adminMember());
        lenient().when(memberService.getMemberById(NON_ADMIN_MEMBER_ID)).thenReturn(nonAdminMember());
    }

    @Test
    @DisplayName("메인페이지 내용이 없으면 새로 생성한다")
    void updateHomeContentCreatesNewHomeContentWhenItDoesNotExist() {
        HomeContentUpdateRequest request = new HomeContentUpdateRequest(
                "YBC는 배드민턴 동아리입니다.",
                "https://storage.googleapis.com/ybc/images/main.jpg",
                2,
                45
        );

        when(homeContentRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(homeContentRepository.saveAndFlush(any(HomeContent.class)))
                .thenAnswer(invocation -> {
                    HomeContent homeContent = invocation.getArgument(0);
                    ReflectionTestUtils.setField(homeContent, "id", 1L);
                    return homeContent;
                });

        HomeContentResponse response = homeContentService.updateHomeContent(ADMIN_MEMBER_ID, request);

        assertThat(response.clubIntroduction()).isEqualTo("YBC는 배드민턴 동아리입니다.");
        assertThat(response.activityImageUrl()).isEqualTo("https://storage.googleapis.com/ybc/images/main.jpg");
        assertThat(response.regularMeetingCount()).isEqualTo(2);
        assertThat(response.memberCount()).isEqualTo(45);

        ArgumentCaptor<HomeContent> captor = ArgumentCaptor.forClass(HomeContent.class);
        verify(homeContentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getClubIntroduction()).isEqualTo("YBC는 배드민턴 동아리입니다.");
        assertThat(captor.getValue().getActivityImageUrl()).isEqualTo("https://storage.googleapis.com/ybc/images/main.jpg");
        assertThat(captor.getValue().getRegularMeetingCount()).isEqualTo(2);
        assertThat(captor.getValue().getMemberCount()).isEqualTo(45);
        verify(managedImageCleanupService).deleteUnusedImages(
                Set.of(),
                Set.of("https://storage.googleapis.com/ybc/images/main.jpg")
        );
    }

    @Test
    @DisplayName("기존 메인페이지 내용이 있으면 수정한다")
    void updateHomeContentUpdatesExistingHomeContent() {
        HomeContent existingHomeContent = HomeContent.create(
                "기존 소개글입니다.",
                "https://storage.googleapis.com/ybc/images/old-main.jpg",
                1,
                30
        );
        ReflectionTestUtils.setField(existingHomeContent, "id", 1L);

        when(homeContentRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existingHomeContent));
        when(homeContentRepository.saveAndFlush(existingHomeContent)).thenReturn(existingHomeContent);

        HomeContentResponse response = homeContentService.updateHomeContent(
                ADMIN_MEMBER_ID,
                new HomeContentUpdateRequest(
                        "수정된 소개글입니다.",
                        "https://storage.googleapis.com/ybc/images/new-main.jpg",
                        2,
                        45
                )
        );

        assertThat(existingHomeContent.getClubIntroduction()).isEqualTo("수정된 소개글입니다.");
        assertThat(existingHomeContent.getActivityImageUrl()).isEqualTo("https://storage.googleapis.com/ybc/images/new-main.jpg");
        assertThat(existingHomeContent.getRegularMeetingCount()).isEqualTo(2);
        assertThat(existingHomeContent.getMemberCount()).isEqualTo(45);
        assertThat(response.clubIntroduction()).isEqualTo("수정된 소개글입니다.");
        verify(managedImageCleanupService).deleteUnusedImages(
                Set.of("https://storage.googleapis.com/ybc/images/old-main.jpg"),
                Set.of("https://storage.googleapis.com/ybc/images/new-main.jpg")
        );
    }

    @Test
    @DisplayName("메인페이지 내용을 조회할 수 있다")
    void getHomeContentReturnsHomeContent() {
        HomeContent homeContent = HomeContent.create(
                "YBC는 배드민턴 동아리입니다.",
                "https://storage.googleapis.com/ybc/images/main.jpg",
                2,
                45
        );
        ReflectionTestUtils.setField(homeContent, "id", 1L);

        when(homeContentRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(homeContent));

        HomeContentResponse response = homeContentService.getHomeContent();

        assertThat(response.clubIntroduction()).isEqualTo("YBC는 배드민턴 동아리입니다.");
        assertThat(response.activityImageUrl()).isEqualTo("https://storage.googleapis.com/ybc/images/main.jpg");
        assertThat(response.regularMeetingCount()).isEqualTo(2);
        assertThat(response.memberCount()).isEqualTo(45);
    }

    @Test
    @DisplayName("메인페이지 내용이 없으면 조회 시 예외가 발생한다")
    void getHomeContentThrowsWhenHomeContentDoesNotExist() {
        when(homeContentRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> homeContentService.getHomeContent())
                .isInstanceOf(YbcException.class)
                .hasMessage("메인페이지 내용을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("관리자가 아니면 메인페이지 내용을 수정할 수 없다")
    void updateHomeContentThrowsWhenMemberIsNotAdmin() {
        assertThatThrownBy(() -> homeContentService.updateHomeContent(
                NON_ADMIN_MEMBER_ID,
                new HomeContentUpdateRequest(
                        "YBC는 배드민턴 동아리입니다.",
                        "https://storage.googleapis.com/ybc/images/main.jpg",
                        2,
                        45
                )
        ))
                .isInstanceOf(YbcException.class)
                .hasMessage("접근 권한이 없는 회원입니다.");

        verify(homeContentRepository, never()).saveAndFlush(any(HomeContent.class));
    }

    @Test
    @DisplayName("필수값이 없으면 메인페이지 내용을 수정할 수 없다")
    void updateHomeContentThrowsWhenRequiredValueIsMissing() {
        assertThatThrownBy(() -> homeContentService.updateHomeContent(
                ADMIN_MEMBER_ID,
                new HomeContentUpdateRequest(
                        "YBC는 배드민턴 동아리입니다.",
                        null,
                        2,
                        45
                )
        ))
                .isInstanceOf(YbcException.class)
                .hasMessage("필수 파라미터 누락");

        verify(homeContentRepository, never()).saveAndFlush(any(HomeContent.class));
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
