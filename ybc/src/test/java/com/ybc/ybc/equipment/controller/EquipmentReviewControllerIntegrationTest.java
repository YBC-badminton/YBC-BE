package com.ybc.ybc.equipment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.MemberPrincipal;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.dto.MemberSummary;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.equipment.domain.Brand;
import com.ybc.ybc.equipment.domain.Equipment;
import com.ybc.ybc.equipment.domain.EquipmentCategory;
import com.ybc.ybc.equipment.domain.EquipmentReview;
import com.ybc.ybc.equipment.dto.request.EquipmentReviewCreateRequest;
import com.ybc.ybc.equipment.repository.BrandRepository;
import com.ybc.ybc.equipment.repository.EquipmentRepository;
import com.ybc.ybc.equipment.repository.EquipmentReviewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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
                4L,
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
        assertThat(review.getRating()).isEqualTo(4L);
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
                                4L,
                                "후기입니다."
                        ))))
                .andExpect(status().isCreated());

        assertThat(brandRepository.count()).isEqualTo(1);
        assertThat(equipmentRepository.count()).isEqualTo(1);

        EquipmentReview review = equipmentReviewRepository.findAll().get(0);
        assertThat(review.getEquipment().getId()).isEqualTo(equipment.getId());
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
                                4L,
                                "후기입니다."
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    private RequestPostProcessor authenticatedMember(Member member) {
        MemberPrincipal principal = MemberPrincipal.from(MemberSummary.from(member));
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
