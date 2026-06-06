package com.ybc.ybc.equipment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.MemberPrincipal;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.dto.MemberSummary;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.brands.domain.Brand;
import com.ybc.ybc.brands.repository.BrandRepository;
import com.ybc.ybc.equipment.domain.Equipment;
import com.ybc.ybc.equipment.domain.EquipmentCategory;
import com.ybc.ybc.equipment.domain.EquipmentReview;
import com.ybc.ybc.equipment.dto.request.EquipmentReviewCreateRequest;
import com.ybc.ybc.equipment.dto.request.EquipmentReviewUpdateRequest;
import com.ybc.ybc.equipment.repository.EquipmentRepository;
import com.ybc.ybc.equipment.repository.EquipmentReviewRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EquipmentReviewControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private EquipmentReviewRepository equipmentReviewRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Test
    @DisplayName("장비 후기를 생성하고 생성된 응답을 반환한다")
    void createEquipmentReview() throws Exception {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "강태호", "3001@example.com")
        );

        EquipmentReviewCreateRequest request = new EquipmentReviewCreateRequest(
                EquipmentCategory.ACCESSORY,
                "WILSON",
                "프로 오버그립",
                1,
                4,
                "땀 흡수가 잘 되고 그립감이 좋습니다. 교체하기도 쉽고 가격도 합리적이에요. 다만 흰색이라 금방 더러워지는 게 단점입니다."
        );

        mockMvc.perform(post("/reviews")
                        .with(authenticatedMember(member))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewId").isNumber())
                .andExpect(jsonPath("$.category").value("ACCESSORY"))
                .andExpect(jsonPath("$.brandName").value("WILSON"))
                .andExpect(jsonPath("$.productName").value("프로 오버그립"))
                .andExpect(jsonPath("$.usageMonth").value(1))
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.content").value(request.content()))
                .andExpect(jsonPath("$.writer").value("강태호"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        EquipmentReview review = equipmentReviewRepository.findAll().get(0);
        assertThat(review.getMember().getId()).isEqualTo(member.getId());
        assertThat(review.getEquipment().getCategory()).isEqualTo(EquipmentCategory.ACCESSORY);
        assertThat(review.getEquipment().getBrand().getName()).isEqualTo("WILSON");
        assertThat(review.getEquipment().getName()).isEqualTo("프로 오버그립");
        assertThat(review.getUsageMonth()).isEqualTo(1);
        assertThat(review.getRating()).isEqualTo(4);
    }

    @Test
    @DisplayName("이미 존재하는 브랜드와 장비가 있으면 재사용하여 장비 후기를 생성한다")
    void createEquipmentReviewReusesExistingBrandAndEquipment() throws Exception {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "강태호", "3001@example.com")
        );
        Brand brand = brandRepository.save(Brand.create("WILSON"));
        Equipment equipment = equipmentRepository.save(
                Equipment.create("프로 오버그립", EquipmentCategory.ACCESSORY, brand)
        );

        mockMvc.perform(post("/reviews")
                        .with(authenticatedMember(member))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EquipmentReviewCreateRequest(
                                EquipmentCategory.ACCESSORY,
                                "WILSON",
                                "프로 오버그립",
                                1,
                                4,
                                "후기입니다."
                        ))))
                .andExpect(status().isCreated());

        assertThat(brandRepository.count()).isEqualTo(1);
        assertThat(equipmentRepository.count()).isEqualTo(1);

        EquipmentReview review = equipmentReviewRepository.findAll().get(0);
        assertThat(review.getEquipment().getId()).isEqualTo(equipment.getId());
    }

    @Test
    @DisplayName("사용 개월 수가 누락되면 장비 후기 생성에 실패한다")
    void createEquipmentReviewThrowsWhenUsageMonthIsMissing() throws Exception {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "강태호", "3001@example.com")
        );

        mockMvc.perform(post("/reviews")
                        .with(authenticatedMember(member))
                        .contentType("application/json")
                        .content("""
                                {
                                  "category": "ACCESSORY",
                                  "brandName": "WILSON",
                                  "productName": "프로 오버그립",
                                  "rating": 4,
                                  "content": "후기입니다."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("사용 개월 수는 필수입니다."));
    }

    @Test
    @DisplayName("평점이 누락되면 장비 후기 생성에 실패한다")
    void createEquipmentReviewThrowsWhenRatingIsMissing() throws Exception {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "강태호", "3001@example.com")
        );

        mockMvc.perform(post("/reviews")
                        .with(authenticatedMember(member))
                        .contentType("application/json")
                        .content("""
                                {
                                  "category": "ACCESSORY",
                                  "brandName": "WILSON",
                                  "productName": "프로 오버그립",
                                  "usageMonth": 1,
                                  "content": "후기입니다."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("평점은 필수입니다."));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 장비 후기를 생성할 수 없다")
    void createEquipmentReviewRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/reviews")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EquipmentReviewCreateRequest(
                                EquipmentCategory.ACCESSORY,
                                "WILSON",
                                "프로 오버그립",
                                1,
                                4,
                                "후기입니다."
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("작성자는 본인 장비 후기를 수정할 수 있다")
    void updateEquipmentReview() throws Exception {
        Member author = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "김민수", "3001@example.com")
        );
        Brand brand = brandRepository.save(Brand.create("YONEX"));
        Equipment equipment = equipmentRepository.save(
                Equipment.create("아스트록스 99 프로", EquipmentCategory.RACKET, brand)
        );
        EquipmentReview review = equipmentReviewRepository.save(
                EquipmentReview.create(5, "기존 후기입니다.", 6, author, equipment)
        );

        mockMvc.perform(patch("/reviews/{reviewId}", review.getId())
                        .with(authenticatedMember(author))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EquipmentReviewUpdateRequest(12, 4, "수정된 후기입니다."))))
                .andExpect(status().isOk());

        EquipmentReview updatedReview = equipmentReviewRepository.findById(review.getId()).orElseThrow();
        assertThat(updatedReview.getUsageMonth()).isEqualTo(12);
        assertThat(updatedReview.getRating()).isEqualTo(4);
        assertThat(updatedReview.getContent()).isEqualTo("수정된 후기입니다.");
    }

    @Test
    @DisplayName("작성자가 아니면 장비 후기를 수정할 수 없다")
    void updateEquipmentReviewThrowsWhenRequesterIsNotAuthor() throws Exception {
        Member author = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "김민수", "3001@example.com")
        );
        Member anotherMember = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3002", "강태호", "3002@example.com")
        );
        Brand brand = brandRepository.save(Brand.create("YONEX"));
        Equipment equipment = equipmentRepository.save(
                Equipment.create("아스트록스 99 프로", EquipmentCategory.RACKET, brand)
        );
        EquipmentReview review = equipmentReviewRepository.save(
                EquipmentReview.create(5, "기존 후기입니다.", 6, author, equipment)
        );

        mockMvc.perform(patch("/reviews/{reviewId}", review.getId())
                        .with(authenticatedMember(anotherMember))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EquipmentReviewUpdateRequest(12, 4, "수정된 후기입니다."))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("REVIEW_UPDATE_FORBIDDEN"));
    }

    @Test
    @DisplayName("존재하지 않는 장비 후기는 수정할 수 없다")
    void updateEquipmentReviewThrowsWhenReviewIsMissing() throws Exception {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "강태호", "3001@example.com")
        );

        mockMvc.perform(patch("/reviews/{reviewId}", 999L)
                        .with(authenticatedMember(member))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EquipmentReviewUpdateRequest(12, 4, "수정된 후기입니다."))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("REVIEW_NOT_FOUND"));
    }

    @Test
    @DisplayName("작성자는 본인 장비 후기를 삭제할 수 있다")
    void deleteEquipmentReview() throws Exception {
        Member author = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "김민수", "3001@example.com")
        );
        Brand brand = brandRepository.save(Brand.create("YONEX"));
        Equipment equipment = equipmentRepository.save(
                Equipment.create("아스트록스 99 프로", EquipmentCategory.RACKET, brand)
        );
        EquipmentReview review = equipmentReviewRepository.save(
                EquipmentReview.create(5, "기존 후기입니다.", 6, author, equipment)
        );

        mockMvc.perform(delete("/reviews/{reviewId}", review.getId())
                        .with(authenticatedMember(author)))
                .andExpect(status().isNoContent());

        assertThat(equipmentReviewRepository.findById(review.getId())).isEmpty();
    }

    @Test
    @DisplayName("작성자가 아니면 장비 후기를 삭제할 수 없다")
    void deleteEquipmentReviewThrowsWhenRequesterIsNotAuthor() throws Exception {
        Member author = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "김민수", "3001@example.com")
        );
        Member anotherMember = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3002", "강태호", "3002@example.com")
        );
        Brand brand = brandRepository.save(Brand.create("YONEX"));
        Equipment equipment = equipmentRepository.save(
                Equipment.create("아스트록스 99 프로", EquipmentCategory.RACKET, brand)
        );
        EquipmentReview review = equipmentReviewRepository.save(
                EquipmentReview.create(5, "기존 후기입니다.", 6, author, equipment)
        );

        mockMvc.perform(delete("/reviews/{reviewId}", review.getId())
                        .with(authenticatedMember(anotherMember)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("REVIEW_DELETE_FORBIDDEN"));
    }

    @Test
    @DisplayName("존재하지 않는 장비 후기는 삭제할 수 없다")
    void deleteEquipmentReviewThrowsWhenReviewIsMissing() throws Exception {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "강태호", "3001@example.com")
        );

        mockMvc.perform(delete("/reviews/{reviewId}", 999L)
                        .with(authenticatedMember(member)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("REVIEW_NOT_FOUND"));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 장비 후기를 수정할 수 없다")
    void updateEquipmentReviewRequiresAuthentication() throws Exception {
        mockMvc.perform(patch("/reviews/{reviewId}", 1L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EquipmentReviewUpdateRequest(12, 4, "수정된 후기입니다."))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 장비 후기를 삭제할 수 없다")
    void deleteEquipmentReviewRequiresAuthentication() throws Exception {
        mockMvc.perform(delete("/reviews/{reviewId}", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("장비 후기 목록을 생성일 내림차순으로 조회한다")
    void getEquipmentReviews() throws Exception {
        Member memberOne = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "김민수", "3001@example.com")
        );
        Member memberTwo = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3002", "오시온", "3002@example.com")
        );
        Brand yonex = brandRepository.save(Brand.create("YONEX"));
        Brand victor = brandRepository.save(Brand.create("VICTOR"));
        Equipment racket = equipmentRepository.save(
                Equipment.create("아스트록스 99 프로", EquipmentCategory.RACKET, yonex)
        );
        Equipment shoes = equipmentRepository.save(
                Equipment.create("P9200TD", EquipmentCategory.SHOES, victor)
        );
        EquipmentReview reviewOne = equipmentReviewRepository.save(
                EquipmentReview.create(
                        5,
                        "헤드헤비 라켓이지만 스윙이 편하고 파워가 좋습니다. 특히 스매싱할 때 위력이 엄청나요. 처음엔 무게감이 느껴졌지만 적응하고 나니 제 실력이 한 단계 올라간 느낌입니다. 중급자 이상에게 강력 추천합니다.",
                        6,
                        memberOne,
                        racket
                )
        );
        EquipmentReview reviewTwo = equipmentReviewRepository.save(
                EquipmentReview.create(
                        4,
                        "쿠션감이 좋고 발목 지지력이 탁월합니다. 장시간 경기에도 발이 편하고 미끄럼 방지 기능이 우수해요. 다만 내구성이 조금 아쉽습니다.",
                        12,
                        memberTwo,
                        shoes
                )
        );
        updateReviewTimestamps(reviewOne.getId(), LocalDateTime.of(2026, 3, 15, 14, 30), LocalDateTime.of(2026, 3, 15, 14, 30));
        updateReviewTimestamps(reviewTwo.getId(), LocalDateTime.of(2026, 3, 14, 11, 20), LocalDateTime.of(2026, 3, 14, 11, 20));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/reviews")
                        .with(authenticatedMember(memberOne))
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews.length()").value(2))
                .andExpect(jsonPath("$.reviews[0].reviewId").value(reviewOne.getId()))
                .andExpect(jsonPath("$.reviews[0].category").value("RACKET"))
                .andExpect(jsonPath("$.reviews[0].brandName").value("YONEX"))
                .andExpect(jsonPath("$.reviews[0].productName").value("아스트록스 99 프로"))
                .andExpect(jsonPath("$.reviews[0].rating").value(5))
                .andExpect(jsonPath("$.reviews[0].usageMonth").value(6))
                .andExpect(jsonPath("$.reviews[0].memberNickname").value("김민수"))
                .andExpect(jsonPath("$.reviews[0].createdAt").value("2026-03-15T14:30:00"))
                .andExpect(jsonPath("$.reviews[1].reviewId").value(reviewTwo.getId()))
                .andExpect(jsonPath("$.reviews[1].category").value("SHOES"))
                .andExpect(jsonPath("$.reviews[1].brandName").value("VICTOR"))
                .andExpect(jsonPath("$.reviews[1].productName").value("P9200TD"))
                .andExpect(jsonPath("$.reviews[1].rating").value(4))
                .andExpect(jsonPath("$.reviews[1].usageMonth").value(12))
                .andExpect(jsonPath("$.reviews[1].memberNickname").value("오시온"))
                .andExpect(jsonPath("$.reviews[1].createdAt").value("2026-03-14T11:20:00"))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(2))
                .andExpect(jsonPath("$.pageInfo.totalPages").value(1))
                .andExpect(jsonPath("$.pageInfo.isLast").value(true));
    }

    @Test
    @DisplayName("카테고리 필터와 오름차순 정렬로 장비 후기 목록을 조회한다")
    void getEquipmentReviewsByCategoryInAscendingOrder() throws Exception {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "김민수", "3001@example.com")
        );
        Brand yonex = brandRepository.save(Brand.create("YONEX"));
        Brand victor = brandRepository.save(Brand.create("VICTOR"));
        Equipment racketOne = equipmentRepository.save(
                Equipment.create("아스트록스 99 프로", EquipmentCategory.RACKET, yonex)
        );
        Equipment racketTwo = equipmentRepository.save(
                Equipment.create("드라이브X 9X", EquipmentCategory.RACKET, victor)
        );
        Equipment shoes = equipmentRepository.save(
                Equipment.create("P9200TD", EquipmentCategory.SHOES, victor)
        );
        EquipmentReview olderReview = equipmentReviewRepository.save(
                EquipmentReview.create(4, "오래된 라켓 후기", 4, member, racketOne)
        );
        EquipmentReview newerReview = equipmentReviewRepository.save(
                EquipmentReview.create(5, "최신 라켓 후기", 8, member, racketTwo)
        );
        equipmentReviewRepository.save(
                EquipmentReview.create(3, "신발 후기", 12, member, shoes)
        );
        updateReviewTimestamps(olderReview.getId(), LocalDateTime.of(2026, 3, 10, 10, 0), LocalDateTime.of(2026, 3, 10, 10, 0));
        updateReviewTimestamps(newerReview.getId(), LocalDateTime.of(2026, 3, 20, 10, 0), LocalDateTime.of(2026, 3, 20, 10, 0));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/reviews")
                        .with(authenticatedMember(member))
                        .param("category", "RACKET")
                        .param("sort", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews.length()").value(2))
                .andExpect(jsonPath("$.reviews[0].reviewId").value(olderReview.getId()))
                .andExpect(jsonPath("$.reviews[1].reviewId").value(newerReview.getId()))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(2));
    }

    @Test
    @DisplayName("유효하지 않은 정렬 조건으로 장비 후기 목록을 조회하면 예외가 발생한다")
    void getEquipmentReviewsThrowsWhenSortIsInvalid() throws Exception {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "김민수", "3001@example.com")
        );

        mockMvc.perform(get("/reviews")
                        .with(authenticatedMember(member))
                        .param("sort", "latest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REVIEW_SORT"));
    }

    @Test
    @DisplayName("페이지 번호가 0보다 작으면 장비 후기 목록 조회에 실패한다")
    void getEquipmentReviewsThrowsWhenPageIsNegative() throws Exception {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "김민수", "3001@example.com")
        );

        mockMvc.perform(get("/reviews")
                        .with(authenticatedMember(member))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("페이지 번호는 0 이상이어야 합니다."));
    }

    @Test
    @DisplayName("페이지 크기가 1보다 작으면 장비 후기 목록 조회에 실패한다")
    void getEquipmentReviewsThrowsWhenSizeIsLessThanOne() throws Exception {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "김민수", "3001@example.com")
        );

        mockMvc.perform(get("/reviews")
                        .with(authenticatedMember(member))
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("페이지 크기는 1 이상이어야 합니다."));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 장비 후기 목록을 조회할 수 없다")
    void getEquipmentReviewsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/reviews"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("장비 후기 상세를 조회한다")
    void getEquipmentReview() throws Exception {
        Member author = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "김민수", "3001@example.com")
        );
        Brand brand = brandRepository.save(Brand.create("YONEX"));
        Equipment equipment = equipmentRepository.save(
                Equipment.create("아스트록스 99 프로", EquipmentCategory.RACKET, brand)
        );
        EquipmentReview review = equipmentReviewRepository.save(
                EquipmentReview.create(
                        5,
                        "헤드헤비 라켓이지만 스윙이 편하고 파워가 좋습니다. 특히 스매싱할 때 위력이 엄청나요. 처음엔 무게감이 느껴졌지만 적응하고 나니 제 실력이 한 단계 올라간 느낌입니다. 중급자 이상에게 강력 추천합니다.",
                        6,
                        author,
                        equipment
                )
        );
        LocalDateTime timestamp = LocalDateTime.of(2026, 3, 15, 14, 30);
        updateReviewTimestamps(review.getId(), timestamp, timestamp);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/reviews/{reviewId}", review.getId())
                        .with(authenticatedMember(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewId").value(review.getId()))
                .andExpect(jsonPath("$.category").value("RACKET"))
                .andExpect(jsonPath("$.brandName").value("YONEX"))
                .andExpect(jsonPath("$.equipmentName").value("아스트록스 99 프로"))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.usageMonth").value(6))
                .andExpect(jsonPath("$.content").value(review.getContent()))
                .andExpect(jsonPath("$.memberId").value(author.getId()))
                .andExpect(jsonPath("$.writer").value("김민수"))
                .andExpect(jsonPath("$.createdAt").value("2026-03-15T14:30:00"))
                .andExpect(jsonPath("$.updatedAt").value("2026-03-15T14:30:00"))
                .andExpect(jsonPath("$.isAuthor").value(true));
    }

    @Test
    @DisplayName("작성자가 아닌 회원이 장비 후기 상세를 조회하면 isAuthor는 false다")
    void getEquipmentReviewSetsIsAuthorFalseForNonAuthor() throws Exception {
        Member author = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "김민수", "3001@example.com")
        );
        Member viewer = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3002", "강태호", "3002@example.com")
        );
        Brand brand = brandRepository.save(Brand.create("YONEX"));
        Equipment equipment = equipmentRepository.save(
                Equipment.create("아스트록스 99 프로", EquipmentCategory.RACKET, brand)
        );
        EquipmentReview review = equipmentReviewRepository.save(
                EquipmentReview.create(5, "후기입니다.", 6, author, equipment)
        );

        mockMvc.perform(get("/reviews/{reviewId}", review.getId())
                        .with(authenticatedMember(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAuthor").value(false));
    }

    @Test
    @DisplayName("존재하지 않는 장비 후기 상세를 조회하면 예외가 발생한다")
    void getEquipmentReviewThrowsWhenReviewIsMissing() throws Exception {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "강태호", "3001@example.com")
        );

        mockMvc.perform(get("/reviews/{reviewId}", 999L)
                        .with(authenticatedMember(member)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("REVIEW_NOT_FOUND"));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 장비 후기 상세를 조회할 수 없다")
    void getEquipmentReviewRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/reviews/{reviewId}", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    private void updateReviewTimestamps(Long reviewId, LocalDateTime createdAt, LocalDateTime updatedAt) {
        jdbcTemplate.update(
                "update equipment_review set created_at = ?, updated_at = ? where equipment_review_id = ?",
                createdAt,
                updatedAt,
                reviewId
        );
    }

    private RequestPostProcessor authenticatedMember(Member member) {
        MemberPrincipal principal = MemberPrincipal.from(MemberSummary.from(member));
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
