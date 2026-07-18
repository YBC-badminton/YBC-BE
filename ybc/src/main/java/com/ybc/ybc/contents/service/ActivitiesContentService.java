package com.ybc.ybc.contents.service;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.service.MemberService;
import com.ybc.ybc.contents.domain.ActivitiesContent;
import com.ybc.ybc.contents.domain.ActivitiesEvent;
import com.ybc.ybc.contents.dto.request.ActivitiesContentUpdateRequest;
import com.ybc.ybc.contents.dto.response.ActivitiesContentResponse;
import com.ybc.ybc.contents.repository.ActivitiesContentRepository;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.images.service.ManagedImageCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivitiesContentService {
    private static final int REGULAR_MEETING_IMAGE_COUNT = 2;
    private static final int EVENT_COUNT = 2;
    private static final int AFTER_PARTY_DESCRIPTION_COUNT = 3;

    private final MemberService memberService;
    private final ActivitiesContentRepository activitiesContentRepository;
    private final ManagedImageCleanupService managedImageCleanupService;

    public ActivitiesContentResponse getActivitiesContent() {
        return ActivitiesContentResponse.from(getActivitiesContentEntity());
    }

    @Transactional
    public ActivitiesContentResponse updateActivitiesContent(Long memberId, ActivitiesContentUpdateRequest request) {
        validateAdmin(memberId);
        validateRequest(request);

        List<ActivitiesEvent> events = request.events().stream()
                .map(event -> ActivitiesEvent.create(event.title(), event.description(), event.imageUrl()))
                .toList();

        Optional<ActivitiesContent> existingActivitiesContent = activitiesContentRepository.findFirstByOrderByIdAsc();
        Set<String> previousImageUrls = existingActivitiesContent
                .map(this::extractImageUrls)
                .orElseGet(Set::of);

        ActivitiesContent activitiesContent = existingActivitiesContent
                .map(savedActivitiesContent -> {
                    savedActivitiesContent.update(
                            request.mainTitle(),
                            request.subDescription(),
                            request.regularMeeting().description(),
                            request.regularMeeting().imageUrls(),
                            events,
                            request.afterParties().description(),
                            request.afterParties().imageUrl()
                    );
                    return savedActivitiesContent;
                })
                .orElseGet(() -> ActivitiesContent.create(
                        request.mainTitle(),
                        request.subDescription(),
                        request.regularMeeting().description(),
                        request.regularMeeting().imageUrls(),
                        events,
                        request.afterParties().description(),
                        request.afterParties().imageUrl()
                ));

        ActivitiesContent savedActivitiesContent = activitiesContentRepository.saveAndFlush(activitiesContent);
        managedImageCleanupService.deleteUnusedImages(previousImageUrls, extractImageUrls(savedActivitiesContent));
        return ActivitiesContentResponse.from(savedActivitiesContent);
    }

    private void validateAdmin(Long memberId) {
        Member member = memberService.getMemberById(memberId);
        if (!member.isAdmin()) {
            throw new YbcException(ExceptionCode.UNAUTHORIZED_USER);
        }
    }

    private void validateRequest(ActivitiesContentUpdateRequest request) {
        if (request == null
                || !StringUtils.hasText(request.mainTitle())
                || !StringUtils.hasText(request.subDescription())
                || request.regularMeeting() == null
                || !StringUtils.hasText(request.regularMeeting().description())
                || !isValidImageUrls(request.regularMeeting().imageUrls(), REGULAR_MEETING_IMAGE_COUNT)
                || !isValidEvents(request.events())
                || request.afterParties() == null
                || !isValidDescriptions(request.afterParties().description(), AFTER_PARTY_DESCRIPTION_COUNT)
                || !StringUtils.hasText(request.afterParties().imageUrl())) {
            throw new YbcException(ExceptionCode.ILLEGAL_ARGUMENT);
        }
    }

    private boolean isValidImageUrls(List<String> imageUrls, int expectedSize) {
        return imageUrls != null
                && imageUrls.size() == expectedSize
                && imageUrls.stream().allMatch(StringUtils::hasText);
    }

    private boolean isValidEvents(List<ActivitiesContentUpdateRequest.ActivityEventRequest> events) {
        return events != null
                && events.size() == EVENT_COUNT
                && events.stream().allMatch(event ->
                        event != null
                                && StringUtils.hasText(event.title())
                                && StringUtils.hasText(event.description())
                                && StringUtils.hasText(event.imageUrl())
                );
    }

    private boolean isValidDescriptions(List<String> descriptions, int expectedSize) {
        return descriptions != null
                && descriptions.size() == expectedSize
                && descriptions.stream().allMatch(StringUtils::hasText);
    }

    private ActivitiesContent getActivitiesContentEntity() {
        return activitiesContentRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new YbcException(ExceptionCode.ACTIVITIES_CONTENT_NOT_FOUND));
    }

    private Set<String> extractImageUrls(ActivitiesContent activitiesContent) {
        Set<String> imageUrls = new HashSet<>(activitiesContent.getRegularMeetingImageUrls());
        activitiesContent.getEvents().stream()
                .map(ActivitiesEvent::getImageUrl)
                .forEach(imageUrls::add);
        imageUrls.add(activitiesContent.getAfterPartyImageUrl());
        return imageUrls;
    }
}
