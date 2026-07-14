package com.ybc.ybc.contents.service;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.service.MemberService;
import com.ybc.ybc.contents.domain.ActivitiesContent;
import com.ybc.ybc.contents.dto.request.ActivitiesContentUpdateRequest;
import com.ybc.ybc.contents.dto.response.ActivitiesContentResponse;
import com.ybc.ybc.contents.repository.ActivitiesContentRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivitiesContentServiceTest {
    private static final Long ADMIN_MEMBER_ID = 1L;
    private static final Long NON_ADMIN_MEMBER_ID = 2L;

    @Mock
    private MemberService memberService;

    @Mock
    private ActivitiesContentRepository activitiesContentRepository;

    @InjectMocks
    private ActivitiesContentService activitiesContentService;

    @BeforeEach
    void setUp() {
        lenient().when(memberService.getMemberById(ADMIN_MEMBER_ID)).thenReturn(adminMember());
        lenient().when(memberService.getMemberById(NON_ADMIN_MEMBER_ID)).thenReturn(nonAdminMember());
    }

    @Test
    @DisplayName("지난 활동 내용이 없으면 새로 생성한다")
    void updateActivitiesContentCreatesNewActivitiesContentWhenItDoesNotExist() {
        ActivitiesContentUpdateRequest request = createValidRequest();

        when(activitiesContentRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(activitiesContentRepository.saveAndFlush(any(ActivitiesContent.class)))
                .thenAnswer(invocation -> {
                    ActivitiesContent activitiesContent = invocation.getArgument(0);
                    ReflectionTestUtils.setField(activitiesContent, "id", 1L);
                    return activitiesContent;
                });

        ActivitiesContentResponse response = activitiesContentService.updateActivitiesContent(ADMIN_MEMBER_ID, request);

        assertThat(response.mainTitle()).isEqualTo("YBC의 지난 활동");
        assertThat(response.regularMeeting().imageUrls()).containsExactly(
                "https://storage.googleapis.com/ybc/images/regular1.jpg",
                "https://storage.googleapis.com/ybc/images/regular2.jpg"
        );
        assertThat(response.events()).hasSize(2);
        assertThat(response.afterParties().description()).containsExactly("운동 후 식사", "친목 활동", "뒤풀이 진행");

        ArgumentCaptor<ActivitiesContent> captor = ArgumentCaptor.forClass(ActivitiesContent.class);
        verify(activitiesContentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getMainTitle()).isEqualTo("YBC의 지난 활동");
        assertThat(captor.getValue().getEvents()).hasSize(2);
        assertThat(captor.getValue().getAfterPartyDescriptions()).containsExactly("운동 후 식사", "친목 활동", "뒤풀이 진행");
    }

    @Test
    @DisplayName("기존 지난 활동 내용이 있으면 수정한다")
    void updateActivitiesContentUpdatesExistingActivitiesContent() {
        ActivitiesContent existingActivitiesContent = ActivitiesContent.create(
                "기존 타이틀",
                "기존 설명",
                "기존 정기모임 설명",
                List.of(
                        "https://storage.googleapis.com/ybc/images/old-regular1.jpg",
                        "https://storage.googleapis.com/ybc/images/old-regular2.jpg"
                ),
                List.of(),
                List.of("기존 뒤풀이 1", "기존 뒤풀이 2", "기존 뒤풀이 3"),
                "https://storage.googleapis.com/ybc/images/old-after-party.jpg"
        );
        ReflectionTestUtils.setField(existingActivitiesContent, "id", 1L);

        when(activitiesContentRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existingActivitiesContent));
        when(activitiesContentRepository.saveAndFlush(existingActivitiesContent)).thenReturn(existingActivitiesContent);

        ActivitiesContentResponse response = activitiesContentService.updateActivitiesContent(
                ADMIN_MEMBER_ID,
                createValidRequest()
        );

        assertThat(existingActivitiesContent.getMainTitle()).isEqualTo("YBC의 지난 활동");
        assertThat(existingActivitiesContent.getRegularMeetingDescription()).isEqualTo("매주 정기적으로 운동을 진행합니다.");
        assertThat(existingActivitiesContent.getRegularMeetingImageUrls()).containsExactly(
                "https://storage.googleapis.com/ybc/images/regular1.jpg",
                "https://storage.googleapis.com/ybc/images/regular2.jpg"
        );
        assertThat(existingActivitiesContent.getEvents()).hasSize(2);
        assertThat(existingActivitiesContent.getAfterPartyDescriptions()).containsExactly("운동 후 식사", "친목 활동", "뒤풀이 진행");
        assertThat(response.events().get(0).title()).isEqualTo("리그전");
    }

    @Test
    @DisplayName("지난 활동 내용을 조회할 수 있다")
    void getActivitiesContentReturnsActivitiesContent() {
        ActivitiesContent activitiesContent = ActivitiesContent.create(
                "YBC의 지난 활동",
                "YBC의 다양한 활동을 소개합니다.",
                "매주 정기적으로 운동을 진행합니다.",
                List.of(
                        "https://storage.googleapis.com/ybc/images/regular1.jpg",
                        "https://storage.googleapis.com/ybc/images/regular2.jpg"
                ),
                List.of(),
                List.of("운동 후 식사", "친목 활동", "뒤풀이 진행"),
                "https://storage.googleapis.com/ybc/images/after-party.jpg"
        );
        ReflectionTestUtils.setField(activitiesContent, "id", 1L);

        when(activitiesContentRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(activitiesContent));

        ActivitiesContentResponse response = activitiesContentService.getActivitiesContent();

        assertThat(response.mainTitle()).isEqualTo("YBC의 지난 활동");
        assertThat(response.afterParties().description()).containsExactly("운동 후 식사", "친목 활동", "뒤풀이 진행");
    }

    @Test
    @DisplayName("지난 활동 내용이 없으면 조회 시 예외가 발생한다")
    void getActivitiesContentThrowsWhenActivitiesContentDoesNotExist() {
        when(activitiesContentRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activitiesContentService.getActivitiesContent())
                .isInstanceOf(YbcException.class)
                .hasMessage("지난 활동 내용을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("관리자가 아니면 지난 활동 내용을 수정할 수 없다")
    void updateActivitiesContentThrowsWhenMemberIsNotAdmin() {
        assertThatThrownBy(() -> activitiesContentService.updateActivitiesContent(
                NON_ADMIN_MEMBER_ID,
                createValidRequest()
        ))
                .isInstanceOf(YbcException.class)
                .hasMessage("접근 권한이 없는 회원입니다.");

        verify(activitiesContentRepository, never()).saveAndFlush(any(ActivitiesContent.class));
    }

    @Test
    @DisplayName("필수값 개수가 맞지 않으면 지난 활동 내용을 수정할 수 없다")
    void updateActivitiesContentThrowsWhenRequiredValueCountIsInvalid() {
        ActivitiesContentUpdateRequest invalidRequest = new ActivitiesContentUpdateRequest(
                "YBC의 지난 활동",
                "YBC의 다양한 활동을 소개합니다.",
                new ActivitiesContentUpdateRequest.RegularMeetingRequest(
                        "매주 정기적으로 운동을 진행합니다.",
                        List.of("https://storage.googleapis.com/ybc/images/regular1.jpg")
                ),
                List.of(
                        new ActivitiesContentUpdateRequest.ActivityEventRequest(
                                "리그전",
                                "부원들과 함께하는 리그전",
                                "https://storage.googleapis.com/ybc/images/event1.jpg"
                        ),
                        new ActivitiesContentUpdateRequest.ActivityEventRequest(
                                "MT",
                                "친목을 위한 MT",
                                "https://storage.googleapis.com/ybc/images/event2.jpg"
                        )
                ),
                new ActivitiesContentUpdateRequest.AfterPartiesRequest(
                        List.of("운동 후 식사", "친목 활동", "뒤풀이 진행"),
                        "https://storage.googleapis.com/ybc/images/after-party.jpg"
                )
        );

        assertThatThrownBy(() -> activitiesContentService.updateActivitiesContent(
                ADMIN_MEMBER_ID,
                invalidRequest
        ))
                .isInstanceOf(YbcException.class)
                .hasMessage("필수 파라미터 누락");

        verify(activitiesContentRepository, never()).saveAndFlush(any(ActivitiesContent.class));
    }

    private ActivitiesContentUpdateRequest createValidRequest() {
        return new ActivitiesContentUpdateRequest(
                "YBC의 지난 활동",
                "YBC의 다양한 활동을 소개합니다.",
                new ActivitiesContentUpdateRequest.RegularMeetingRequest(
                        "매주 정기적으로 운동을 진행합니다.",
                        List.of(
                                "https://storage.googleapis.com/ybc/images/regular1.jpg",
                                "https://storage.googleapis.com/ybc/images/regular2.jpg"
                        )
                ),
                List.of(
                        new ActivitiesContentUpdateRequest.ActivityEventRequest(
                                "리그전",
                                "부원들과 함께하는 리그전",
                                "https://storage.googleapis.com/ybc/images/event1.jpg"
                        ),
                        new ActivitiesContentUpdateRequest.ActivityEventRequest(
                                "MT",
                                "친목을 위한 MT",
                                "https://storage.googleapis.com/ybc/images/event2.jpg"
                        )
                ),
                new ActivitiesContentUpdateRequest.AfterPartiesRequest(
                        List.of("운동 후 식사", "친목 활동", "뒤풀이 진행"),
                        "https://storage.googleapis.com/ybc/images/after-party.jpg"
                )
        );
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
