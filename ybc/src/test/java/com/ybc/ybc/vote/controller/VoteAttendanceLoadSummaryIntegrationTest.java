package com.ybc.ybc.vote.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.MemberPrincipal;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.dto.MemberSummary;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.support.TestMailConfig;
import com.ybc.ybc.vote.domain.ActivityType;
import com.ybc.ybc.vote.domain.Vote;
import com.ybc.ybc.vote.dto.request.VoteAttendanceRequest;
import com.ybc.ybc.vote.repository.GuestRepository;
import com.ybc.ybc.vote.repository.VoteAttendanceRepository;
import com.ybc.ybc.vote.repository.VoteMatchGroupRepository;
import com.ybc.ybc.vote.repository.VoteMatchRepository;
import com.ybc.ybc.vote.repository.VoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest(properties = {
        "logging.level.root=OFF",
        "logging.level.org.springframework=OFF",
        "logging.level.org.hibernate=OFF",
        "spring.main.banner-mode=off"
})
@AutoConfigureMockMvc
@Import(TestMailConfig.class)
class VoteAttendanceLoadSummaryIntegrationTest {
    private static final int DEFAULT_CONCURRENT_REQUESTS = 100;
    private static final int DEFAULT_REGULAR_VOTE_CAPACITY = 24;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private VoteAttendanceRepository voteAttendanceRepository;

    @Autowired
    private GuestRepository guestRepository;

    @Autowired
    private VoteMatchRepository voteMatchRepository;

    @Autowired
    private VoteMatchGroupRepository voteMatchGroupRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @BeforeEach
    void setUp() {
        resetState();
    }

    @Test
    @DisplayName("참가 신청 동시성 검증 요약을 출력한다")
    void printLoadTestSummary() throws Exception {
        int concurrentRequests = concurrentRequests();
        int regularVoteCapacity = regularVoteCapacity();

        DistinctUserLoadResult distinctUserLoadResult = runDistinctUserLoadTest(concurrentRequests, regularVoteCapacity);
        long duplicateCount = runDuplicateUserLoadTest(concurrentRequests, regularVoteCapacity);

        assertThat(distinctUserLoadResult.confirmedCount()).isEqualTo(regularVoteCapacity);
        assertThat(distinctUserLoadResult.waitingCount()).isEqualTo(concurrentRequests - regularVoteCapacity);
        assertThat(duplicateCount).isZero();

        System.out.printf(
                "%nLoad Test%n%n%d Concurrent Users%n%n────────────%n%nConfirmed%n%d%n%nWaiting%n%d%n%nDuplicate%n%d%n%nAvg%n%dms%n%nP95%n%dms%n%n",
                concurrentRequests,
                distinctUserLoadResult.confirmedCount(),
                distinctUserLoadResult.waitingCount(),
                duplicateCount,
                Math.round(distinctUserLoadResult.metrics().averageRequestDurationMs()),
                Math.round(distinctUserLoadResult.metrics().p95RequestDurationMs())
        );
    }

    private DistinctUserLoadResult runDistinctUserLoadTest(int concurrentRequests, int regularVoteCapacity) throws Exception {
        Vote vote = voteRepository.save(vote(
                "포트폴리오용 정기 운동 동시성 검증",
                ActivityType.REGULAR,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusHours(2),
                regularVoteCapacity
        ));
        List<RequestPostProcessor> requestPostProcessors = IntStream.rangeClosed(1, concurrentRequests)
                .mapToObj(index -> authenticatedMember(
                        memberRepository.save(
                                Member.createSocialMember(
                                        SocialProvider.KAKAO,
                                        "portfolio-concurrent-user-" + index,
                                        "portfolio-member-" + index,
                                        "portfolio-member-" + index + "@example.com"
                                )
                        )
                ))
                .toList();

        ConcurrentRequestMetrics metrics = submitAttendanceRequestsConcurrently(vote.getId(), requestPostProcessors);
        JsonNode attendeeResponse = getAttendeeResponse(vote.getId());
        long confirmedCount = countAttendeesByWaiting(attendeeResponse, false);
        long waitingCount = countAttendeesByWaiting(attendeeResponse, true);
        long persistedAttendanceCount = attendanceCount(vote.getId());

        assertThat(metrics.errors()).isEmpty();
        assertThat(metrics.successCount()).isEqualTo(concurrentRequests);
        assertThat(metrics.failureStatusCounts()).isEmpty();
        assertThat(attendeeResponse.path("totalAttendeeCount").asLong()).isEqualTo(concurrentRequests);
        assertThat(persistedAttendanceCount).isEqualTo(concurrentRequests);

        return new DistinctUserLoadResult(confirmedCount, waitingCount, metrics);
    }

    private long runDuplicateUserLoadTest(int concurrentRequests, int regularVoteCapacity) throws Exception {
        resetState();

        Vote vote = voteRepository.save(vote(
                "포트폴리오용 중복 신청 검증",
                ActivityType.REGULAR,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusHours(2),
                regularVoteCapacity
        ));
        Member member = memberRepository.save(
                Member.createSocialMember(
                        SocialProvider.KAKAO,
                        "portfolio-duplicate-user",
                        "portfolio-duplicate-member",
                        "portfolio-duplicate@example.com"
                )
        );
        List<RequestPostProcessor> requestPostProcessors = IntStream.range(0, concurrentRequests)
                .mapToObj(index -> authenticatedMember(member))
                .toList();

        ConcurrentRequestMetrics metrics = submitAttendanceRequestsConcurrently(vote.getId(), requestPostProcessors);
        long persistedAttendanceCount = attendanceCount(vote.getId(), member.getId());

        assertThat(metrics.errors()).isEmpty();
        assertThat(metrics.successCount()).isEqualTo(concurrentRequests);
        assertThat(metrics.failureStatusCounts()).isEmpty();
        assertThat(persistedAttendanceCount).isEqualTo(1L);

        return Math.max(persistedAttendanceCount - 1L, 0L);
    }

    private int concurrentRequests() {
        return integerSetting("vote.concurrent.requests", "VOTE_CONCURRENT_REQUESTS", DEFAULT_CONCURRENT_REQUESTS);
    }

    private int regularVoteCapacity() {
        return integerSetting("vote.concurrent.capacity", "VOTE_CONCURRENT_CAPACITY", DEFAULT_REGULAR_VOTE_CAPACITY);
    }

    private int integerSetting(String systemPropertyKey, String environmentKey, int defaultValue) {
        String environmentValue = System.getenv(environmentKey);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return Integer.parseInt(environmentValue);
        }
        return Integer.getInteger(systemPropertyKey, defaultValue);
    }

    private ConcurrentRequestMetrics submitAttendanceRequestsConcurrently(Long voteId,
                                                                         List<RequestPostProcessor> requestPostProcessors) throws Exception {
        String requestBody = objectMapper.writeValueAsString(new VoteAttendanceRequest(true));
        ExecutorService executorService = Executors.newFixedThreadPool(requestPostProcessors.size());
        CountDownLatch readyLatch = new CountDownLatch(requestPostProcessors.size());
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestPostProcessors.size());
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        List<Long> requestDurationsNanos = Collections.synchronizedList(new ArrayList<>());
        ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();

        try {
            for (RequestPostProcessor requestPostProcessor : requestPostProcessors) {
                executorService.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        long requestStartNanos = System.nanoTime();
                        MvcResult mvcResult = mockMvc.perform(put("/votes/{voteId}/attendance", voteId)
                                        .with(requestPostProcessor)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(requestBody))
                                .andReturn();
                        long responseCompletedAt = System.nanoTime();
                        requestDurationsNanos.add(responseCompletedAt - requestStartNanos);
                        statuses.add(mvcResult.getResponse().getStatus());
                    } catch (Exception exception) {
                        errors.add(exception.getClass().getSimpleName() + ": " + exception.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            boolean allReady = readyLatch.await(10, TimeUnit.SECONDS);
            assertThat(allReady).isTrue();

            startLatch.countDown();

            boolean allDone = doneLatch.await(60, TimeUnit.SECONDS);
            assertThat(allDone).isTrue();

            return new ConcurrentRequestMetrics(
                    List.copyOf(statuses),
                    List.copyOf(requestDurationsNanos),
                    List.copyOf(errors)
            );
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private JsonNode getAttendeeResponse(Long voteId) throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/votes/{voteId}/attendees", voteId).with(authenticatedMember(
                        memberRepository.save(
                                Member.createSocialMember(
                                        SocialProvider.KAKAO,
                                        "portfolio-viewer-user",
                                        "portfolio-viewer",
                                        "portfolio-viewer@example.com"
                                )
                        )
                )))
                .andReturn();
        return objectMapper.readTree(mvcResult.getResponse().getContentAsByteArray());
    }

    private long countAttendeesByWaiting(JsonNode attendeeResponse, boolean isWaiting) {
        long count = 0L;
        for (JsonNode attendeeNode : attendeeResponse.path("attendees")) {
            if (attendeeNode.path("isWaiting").asBoolean() == isWaiting) {
                count++;
            }
        }
        return count;
    }

    private long attendanceCount(Long voteId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from vote_attendance where vote_id = ?",
                Long.class,
                voteId
        );
    }

    private long attendanceCount(Long voteId, Long memberId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from vote_attendance where vote_id = ? and member_id = ?",
                Long.class,
                voteId,
                memberId
        );
    }

    private RequestPostProcessor authenticatedMember(Member member) {
        MemberPrincipal principal = MemberPrincipal.from(MemberSummary.from(member));
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    private Vote vote(String title,
                      ActivityType activityType,
                      LocalDateTime voteStartAt,
                      LocalDateTime voteEndAt,
                      int capacity) {
        return Vote.builder()
                .title(title)
                .activityType(activityType)
                .activityDate(LocalDate.now().plusDays(1))
                .activityTime("19:00")
                .location("이화체육관")
                .memo("포트폴리오용 동시성 요약 테스트")
                .voteStartAt(voteStartAt)
                .voteEndAt(voteEndAt)
                .capacity(capacity)
                .build();
    }

    private void resetState() {
        voteMatchRepository.deleteAll();
        voteMatchGroupRepository.deleteAll();
        guestRepository.deleteAll();
        voteAttendanceRepository.deleteAll();
        voteRepository.deleteAll();
        memberRepository.deleteAll();
    }

    private record DistinctUserLoadResult(long confirmedCount,
                                          long waitingCount,
                                          ConcurrentRequestMetrics metrics) {
    }

    private record ConcurrentRequestMetrics(List<Integer> statuses,
                                            List<Long> requestDurationsNanos,
                                            List<String> errors) {
        long successCount() {
            return statuses.stream()
                    .filter(status -> status == 200)
                    .count();
        }

        java.util.Map<Integer, Long> failureStatusCounts() {
            return statuses.stream()
                    .filter(status -> status != 200)
                    .collect(Collectors.groupingBy(status -> status, Collectors.counting()));
        }

        double averageRequestDurationMs() {
            return requestDurationsNanos.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0.0) / 1_000_000.0;
        }

        double p95RequestDurationMs() {
            if (requestDurationsNanos.isEmpty()) {
                return 0.0;
            }
            List<Long> sorted = requestDurationsNanos.stream()
                    .sorted()
                    .toList();
            int index = Math.max((int) Math.ceil(sorted.size() * 0.95) - 1, 0);
            return sorted.get(index) / 1_000_000.0;
        }
    }
}
