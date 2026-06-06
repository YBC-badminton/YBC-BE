package com.ybc.ybc.equipment.service;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.service.MemberService;
import com.ybc.ybc.brands.domain.Brand;
import com.ybc.ybc.brands.repository.BrandRepository;
import com.ybc.ybc.equipment.domain.Equipment;
import com.ybc.ybc.equipment.domain.EquipmentCategory;
import com.ybc.ybc.equipment.domain.EquipmentReview;
import com.ybc.ybc.equipment.dto.request.EquipmentReviewCreateRequest;
import com.ybc.ybc.equipment.dto.request.EquipmentReviewUpdateRequest;
import com.ybc.ybc.equipment.dto.response.EquipmentReviewCreateResponse;
import com.ybc.ybc.equipment.dto.response.EquipmentReviewDetailResponse;
import com.ybc.ybc.equipment.dto.response.EquipmentReviewListResponse;
import com.ybc.ybc.equipment.dto.response.EquipmentReviewSummaryResponse;
import com.ybc.ybc.equipment.repository.EquipmentRepository;
import com.ybc.ybc.equipment.repository.EquipmentReviewRepository;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EquipmentReviewService {
    private final EquipmentReviewRepository equipmentReviewRepository;
    private final EquipmentRepository equipmentRepository;
    private final BrandRepository brandRepository;
    private final MemberService memberService;

    @Transactional
    public EquipmentReviewCreateResponse createReview(Long memberId, EquipmentReviewCreateRequest request) {
        Member member = memberService.getMemberById(memberId);
        Brand brand = brandRepository.findByName(request.brandName())
                .orElseGet(() -> brandRepository.save(Brand.create(request.brandName())));
        Equipment equipment = equipmentRepository.findByNameAndCategoryAndBrand_Name(
                        request.productName(),
                        request.category(),
                        request.brandName()
                )
                .orElseGet(() -> equipmentRepository.save(
                        Equipment.create(request.productName(), request.category(), brand)
                ));

        EquipmentReview review = equipmentReviewRepository.save(
                EquipmentReview.create(
                        request.rating(),
                        request.content(),
                        request.usageMonth(),
                        member,
                        equipment
                )
        );

        return EquipmentReviewCreateResponse.from(review);
    }

    public EquipmentReviewListResponse getReviews(EquipmentCategory category, int page, int size, String sort) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(resolveSortDirection(sort), "createdAt"));
        Page<EquipmentReviewSummaryResponse> reviewPage = (category == null
                ? equipmentReviewRepository.findAll(pageable)
                : equipmentReviewRepository.findByEquipment_Category(category, pageable))
                .map(EquipmentReviewSummaryResponse::from);
        return EquipmentReviewListResponse.from(reviewPage);
    }

    public EquipmentReviewDetailResponse getReview(Long reviewId, Long memberId) {
        EquipmentReview review = equipmentReviewRepository.findWithDetailsById(reviewId)
                .orElseThrow(() -> new YbcException(ExceptionCode.REVIEW_NOT_FOUND));
        return EquipmentReviewDetailResponse.from(review, memberId);
    }

    @Transactional
    public void updateReview(Long reviewId, Long memberId, EquipmentReviewUpdateRequest request) {
        EquipmentReview review = getReviewEntity(reviewId);
        validateAuthor(review, memberId, ExceptionCode.REVIEW_UPDATE_FORBIDDEN);
        review.update(request.rating(), request.content(), request.usageMonth());
    }

    @Transactional
    public void deleteReview(Long reviewId, Long memberId) {
        EquipmentReview review = getReviewEntity(reviewId);
        validateAuthor(review, memberId, ExceptionCode.REVIEW_DELETE_FORBIDDEN);
        equipmentReviewRepository.delete(review);
    }

    private EquipmentReview getReviewEntity(Long reviewId) {
        return equipmentReviewRepository.findById(reviewId)
                .orElseThrow(() -> new YbcException(ExceptionCode.REVIEW_NOT_FOUND));
    }

    private void validateAuthor(EquipmentReview review, Long memberId, ExceptionCode exceptionCode) {
        if (!review.getMember().getId().equals(memberId)) {
            throw new YbcException(exceptionCode);
        }
    }

    private Sort.Direction resolveSortDirection(String sort) {
        if ("asc".equalsIgnoreCase(sort)) {
            return Sort.Direction.ASC;
        }
        if ("desc".equalsIgnoreCase(sort)) {
            return Sort.Direction.DESC;
        }
        throw new YbcException(ExceptionCode.INVALID_REVIEW_SORT);
    }
}
