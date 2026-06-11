package com.ybc.ybc.members.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.applications.domain.DiscoverySource;
import com.ybc.ybc.applications.domain.Gender;
import com.ybc.ybc.applications.domain.InterviewTime;
import com.ybc.ybc.applications.domain.Status;
import com.ybc.ybc.applications.repository.ApplicationRepository;
import com.ybc.ybc.members.domain.ClubMember;
import com.ybc.ybc.members.repository.ClubMemberRepository;
import com.ybc.ybc.support.TestMailConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestMailConfig.class)
class AdminMemberControllerIntegrationTest {
    private static final String ADMIN_MEMBERS_API = "/admin/members";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClubMemberRepository clubMemberRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @BeforeEach
    void setUp() {
        clubMemberRepository.deleteAll();
        applicationRepository.deleteAll();
    }

    @Test
    @DisplayName("인증된 사용자는 부원 명단을 조회할 수 있다")
    void getMembersReturnsSummaryAndMembersWhenAuthenticated() throws Exception {
        ClubMember firstMember = saveMember("홍길동", "한국대학교", "010-1234-5678", "03", "2기", true);
        ClubMember secondMember = saveMember("강해린", "미래대학교", "010-5555-6666", "05", "7.5기");
        ClubMember thirdMember = saveMember("이마크", "글로벌대학교", "010-7777-8888", "01", "8기");

        mockMvc.perform(get(ADMIN_MEMBERS_API)
                        .with(authorizedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalMembers").value(3))
                .andExpect(jsonPath("$.summary.mapoResidentCount").value(1))
                .andExpect(jsonPath("$.members[0].memberId").value(firstMember.getId()))
                .andExpect(jsonPath("$.members[0].name").value("홍길동"))
                .andExpect(jsonPath("$.members[0].isMapoResident").value(true))
                .andExpect(jsonPath("$.members[1].memberId").value(secondMember.getId()))
                .andExpect(jsonPath("$.members[1].term").value("7.5기"))
                .andExpect(jsonPath("$.members[1].isMapoResident").value(false))
                .andExpect(jsonPath("$.members[2].memberId").value(thirdMember.getId()))
                .andExpect(jsonPath("$.members[2].court").doesNotExist());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("최종 합격 지원자가 누락되어 있어도 부원 명단 조회 시 자동 보정된다")
    void getMembersReconcilesMissingFinalPassMember() throws Exception {
        Application application = saveApplication("김철수", Status.FINAL_PASS, 24);

        mockMvc.perform(get(ADMIN_MEMBERS_API)
                        .with(authorizedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalMembers").value(1))
                .andExpect(jsonPath("$.summary.mapoResidentCount").value(0))
                .andExpect(jsonPath("$.members[0].name").value("김철수"))
                .andExpect(jsonPath("$.members[0].age").value(expectedBirthYear(24)))
                .andExpect(jsonPath("$.members[0].term").value("미정"))
                .andExpect(jsonPath("$.members[0].isMapoResident").value(false));

        assertThat(clubMemberRepository.findByApplicationId(application.getId())).isPresent();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("최종 합격 지원자의 주소에 마포가 포함되면 부원 명단 자동 보정 시 마포구민으로 저장된다")
    void getMembersSetsMapoResidentWhenFinalPassAddressContainsMapo() throws Exception {
        Application application = saveApplication("김철수", Status.FINAL_PASS, 24, "서울 마포구 연남동");

        mockMvc.perform(get(ADMIN_MEMBERS_API)
                        .with(authorizedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.mapoResidentCount").value(1))
                .andExpect(jsonPath("$.members[0].name").value("김철수"))
                .andExpect(jsonPath("$.members[0].isMapoResident").value(true));

        ClubMember clubMember = clubMemberRepository.findByApplicationId(application.getId()).orElseThrow();
        assertThat(clubMember.isMapoResident()).isTrue();
    }

    @Test
    @DisplayName("인증된 사용자는 PATCH로 부원 정보를 수정할 수 있다")
    void updateMemberReturnsUpdatedMemberWhenAuthenticated() throws Exception {
        ClubMember member = saveMember("홍길동", "한국대학교", "010-0000-0000", "03", "2기");

        mockMvc.perform(patch(ADMIN_MEMBERS_API + "/{memberId}", member.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "홍길동",
                                "age", "02",
                                "university", "한국대학교",
                                "term", "10기",
                                "isMapoResident", true,
                                "email", "hong@example.com",
                                "gender", "MALE"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.phone").value("010-0000-0000"))
                .andExpect(jsonPath("$.email").value("hong@example.com"))
                .andExpect(jsonPath("$.gender").value("MALE"))
                .andExpect(jsonPath("$.age").value("02"))
                .andExpect(jsonPath("$.term").value("10기"))
                .andExpect(jsonPath("$.isMapoResident").value(true))
                .andExpect(jsonPath("$.court").doesNotExist());

        ClubMember updatedMember = clubMemberRepository.findById(member.getId()).orElseThrow();
        assertThat(updatedMember.getAge()).isEqualTo("02");
        assertThat(updatedMember.isMapoResident()).isTrue();
    }

    @Test
    @DisplayName("PUT 요청으로는 부원 정보를 수정할 수 없다")
    void updateMemberReturnsMethodNotAllowedWhenUsingPut() throws Exception {
        ClubMember member = saveMember("홍길동", "한국대학교", "010-0000-0000", "03", "2기");

        mockMvc.perform(put(ADMIN_MEMBERS_API + "/{memberId}", member.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "홍길동",
                                "age", "02",
                                "university", "한국대학교",
                                "term", "10기",
                                "isMapoResident", true,
                                "email", "hong@example.com",
                                "gender", "MALE"
                        ))))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("인증된 사용자는 이름으로 부원을 검색할 수 있다")
    void searchMemberReturnsMemberWhenAuthenticated() throws Exception {
        ClubMember member = saveMember("홍길동", "한국대학교", "010-1234-5678", "03", "2기");

        mockMvc.perform(get(ADMIN_MEMBERS_API + "/search")
                        .param("name", "홍길동")
                        .with(authorizedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.phone").value("010-1234-5678"));
    }

    @Test
    @DisplayName("인증된 사용자는 부원을 추가할 수 있다")
    void createMemberReturnsCreatedMemberWhenAuthenticated() throws Exception {
        mockMvc.perform(post(ADMIN_MEMBERS_API)
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "홍길동",
                                "university", "한국대학교",
                                "phone", "010-1234-5678",
                                "age", "03",
                                "term", "2기",
                                "isMapoResident", true,
                                "email", "hong@example.com",
                                "gender", "MALE"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").isNumber())
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.email").value("hong@example.com"))
                .andExpect(jsonPath("$.gender").value("MALE"))
                .andExpect(jsonPath("$.age").value("03"))
                .andExpect(jsonPath("$.isMapoResident").value(true))
                .andExpect(jsonPath("$.court").doesNotExist());

        assertThat(clubMemberRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("인증된 사용자는 부원을 삭제할 수 있다")
    void deleteMemberRemovesMemberWhenAuthenticated() throws Exception {
        ClubMember member = saveMember("홍길동", "한국대학교", "010-1234-5678", "03", "2기");

        mockMvc.perform(delete(ADMIN_MEMBERS_API + "/{memberId}", member.getId())
                        .with(authorizedUser()))
                .andExpect(status().isOk());

        assertThat(clubMemberRepository.findById(member.getId())).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 부원 검색 시 404를 반환한다")
    void searchMemberReturnsNotFoundWhenMemberDoesNotExist() throws Exception {
        mockMvc.perform(get(ADMIN_MEMBERS_API + "/search")
                        .param("name", "홍길동")
                        .with(authorizedUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("이메일이 없으면 400을 반환한다")
    void createMemberReturnsBadRequestWhenEmailIsMissing() throws Exception {
        mockMvc.perform(post(ADMIN_MEMBERS_API)
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "홍길동",
                                "university", "한국대학교",
                                "phone", "010-1234-5678",
                                "age", "03",
                                "term", "2기",
                                "gender", "MALE"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ILLEGAL_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("이메일을 입력해 주세요."));
    }

    @Test
    @DisplayName("인증 정보가 없으면 부원 명단 조회는 401을 반환한다")
    void getMembersReturnsUnauthorizedWithoutAccessToken() throws Exception {
        mockMvc.perform(get(ADMIN_MEMBERS_API))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    private ClubMember saveMember(String name,
                                  String university,
                                  String phone,
                                  String age,
                                  String term) {
        return saveMember(name, university, phone, age, term, false);
    }

    private ClubMember saveMember(String name,
                                  String university,
                                  String phone,
                                  String age,
                                  String term,
                                  boolean isMapoResident) {
        ClubMember clubMember = ClubMember.create(name, university, phone, age, term, isMapoResident, null, null);
        ClubMember savedMember = clubMemberRepository.saveAndFlush(clubMember);
        return clubMemberRepository.findById(savedMember.getId()).orElseThrow();
    }

    private Application saveApplication(String name, Status status, int age) {
        return saveApplication(name, status, age, "서울 강서구");
    }

    private Application saveApplication(String name, Status status, int age, String address) {
        Application application = Application.builder()
                .name(name)
                .address(address)
                .gender(Gender.MALE)
                .age(age)
                .phone("010-1234-5678")
                .term("10기")
                .university("한국대학교")
                .major("컴퓨터공학과")
                .introduction("성실하게 활동할 자신 있습니다.")
                .motivation("운동을 좋아합니다.")
                .equipment("개인 라켓 보유")
                .discoverySource(DiscoverySource.INSTAGRAM)
                .discoveryEtc(null)
                .wantsStaff(false)
                .finalComment("열심히 하겠습니다.")
                .status(status)
                .email(name + "@example.com")
                .build();
        application.addInterviewTime(InterviewTime.FIRST_SESSION);
        Application savedApplication = applicationRepository.saveAndFlush(application);
        return applicationRepository.findById(savedApplication.getId()).orElseThrow();
    }

    private String expectedBirthYear(int age) {
        int birthYear = java.time.Year.now().getValue() - age + 1;
        return String.format("%02d", Math.floorMod(birthYear, 100));
    }

    private JwtRequestPostProcessor authorizedUser() {
        return jwt().jwt(jwt -> jwt
                .subject("1")
                .claim("tokenType", "access")
                .claim("provider", "KAKAO")
                .claim("socialId", "123456789")
                .claim("email", "admin@example.com")
                .claim("nickname", "관리자"));
    }
}
