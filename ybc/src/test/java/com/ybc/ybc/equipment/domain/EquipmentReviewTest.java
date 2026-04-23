package com.ybc.ybc.equipment.domain;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.global.exception.YbcException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EquipmentReviewTest {

    @Test
    @DisplayName("장비 후기는 평점이 1 이상 5 이하여야 한다")
    void createFailsWhenRatingIsOutOfRange() {
        assertThatThrownBy(() -> EquipmentReview.create(
                6L,
                "리뷰 내용",
                12,
                createMember(),
                createEquipment()
        ))
                .isInstanceOfSatisfying(YbcException.class, exception ->
                        assertThat(exception.getExceptionCodeName()).isEqualTo("INVALID_EQUIPMENT_REVIEW_RATING"));
    }

    @Test
    @DisplayName("유효한 평점이면 장비 후기를 생성한다")
    void createSucceedsWhenRatingIsValid() {
        EquipmentReview review = EquipmentReview.create(
                5L,
                "리뷰 내용",
                12,
                createMember(),
                createEquipment()
        );

        assertThat(review.getRating()).isEqualTo(5L);
        assertThat(review.getContent()).isEqualTo("리뷰 내용");
        assertThat(review.getUsageMonth()).isEqualTo(12);
    }

    private Member createMember() {
        return Member.createSocialMember(
                SocialProvider.KAKAO,
                "social-id",
                "nickname",
                "test@ybc.com"
        );
    }

    private Equipment createEquipment() {
        Brand brand = Brand.create("Yonex");
        return Equipment.create("Astrox 88D", EquipmentCategory.RACKET, brand);
    }
}
