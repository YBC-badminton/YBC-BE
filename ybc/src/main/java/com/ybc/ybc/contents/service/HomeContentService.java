package com.ybc.ybc.contents.service;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.service.MemberService;
import com.ybc.ybc.contents.domain.HomeContent;
import com.ybc.ybc.contents.dto.request.HomeContentUpdateRequest;
import com.ybc.ybc.contents.dto.response.HomeContentResponse;
import com.ybc.ybc.contents.repository.HomeContentRepository;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeContentService {
    private final MemberService memberService;
    private final HomeContentRepository homeContentRepository;

    public HomeContentResponse getHomeContent() {
        return HomeContentResponse.from(getHomeContentEntity());
    }

    @Transactional
    public HomeContentResponse updateHomeContent(Long memberId, HomeContentUpdateRequest request) {
        validateAdmin(memberId);
        validateRequest(request);

        HomeContent homeContent = homeContentRepository.findFirstByOrderByIdAsc()
                .map(existingHomeContent -> {
                    existingHomeContent.update(
                            request.clubIntroduction(),
                            request.activityImageUrl(),
                            request.regularMeetingCount(),
                            request.memberCount()
                    );
                    return existingHomeContent;
                })
                .orElseGet(() -> HomeContent.create(
                        request.clubIntroduction(),
                        request.activityImageUrl(),
                        request.regularMeetingCount(),
                        request.memberCount()
                ));

        HomeContent savedHomeContent = homeContentRepository.saveAndFlush(homeContent);
        return HomeContentResponse.from(savedHomeContent);
    }

    private void validateAdmin(Long memberId) {
        Member member = memberService.getMemberById(memberId);
        if (!member.isAdmin()) {
            throw new YbcException(ExceptionCode.UNAUTHORIZED_USER);
        }
    }

    private void validateRequest(HomeContentUpdateRequest request) {
        if (request == null
                || !StringUtils.hasText(request.clubIntroduction())
                || !StringUtils.hasText(request.activityImageUrl())
                || request.regularMeetingCount() == null
                || request.memberCount() == null) {
            throw new YbcException(ExceptionCode.ILLEGAL_ARGUMENT);
        }
    }

    private HomeContent getHomeContentEntity() {
        return homeContentRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new YbcException(ExceptionCode.HOME_CONTENT_NOT_FOUND));
    }
}
