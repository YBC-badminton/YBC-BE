package com.ybc.ybc.faqs.service;

import com.ybc.ybc.faqs.domain.Faq;
import com.ybc.ybc.faqs.dto.request.FaqCreateRequest;
import com.ybc.ybc.faqs.dto.request.FaqUpdateRequest;
import com.ybc.ybc.faqs.dto.response.FaqListResponse;
import com.ybc.ybc.faqs.dto.response.FaqResponse;
import com.ybc.ybc.faqs.repository.FaqRepository;
import com.ybc.ybc.global.exception.YbcException;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FaqServiceTest {
    @Mock
    private FaqRepository faqRepository;

    @InjectMocks
    private FaqService faqService;

    @Test
    @DisplayName("FAQ 목록 조회 시 질문과 답변 목록을 반환한다")
    void getFaqsReturnsFaqList() {
        Faq firstFaq = savedFaq(1L, "초보자도 가입할 수 있나요?", "네. 초보자도 가입 가능합니다.");
        Faq secondFaq = savedFaq(2L, "라켓이 없어도 되나요?", "네. 대여 라켓을 이용할 수 있습니다.");
        when(faqRepository.findAllByOrderByIdAsc()).thenReturn(List.of(firstFaq, secondFaq));

        FaqListResponse response = faqService.getFaqs();

        assertThat(response.faqs()).hasSize(2);
        assertThat(response.faqs().get(0).faqId()).isEqualTo(1L);
        assertThat(response.faqs().get(0).question()).isEqualTo("초보자도 가입할 수 있나요?");
        assertThat(response.faqs().get(1).answer()).isEqualTo("네. 대여 라켓을 이용할 수 있습니다.");
    }

    @Test
    @DisplayName("FAQ가 없으면 FAQ 목록 조회 시 예외가 발생한다")
    void getFaqsThrowsWhenFaqIsMissing() {
        when(faqRepository.findAllByOrderByIdAsc()).thenReturn(List.of());

        assertThatThrownBy(() -> faqService.getFaqs())
                .isInstanceOf(YbcException.class)
                .hasMessage("FAQ 정보를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("FAQ 등록 시 저장된 FAQ를 반환한다")
    void createFaqSavesFaq() {
        when(faqRepository.save(any(Faq.class))).thenAnswer(invocation -> {
            Faq faq = invocation.getArgument(0);
            ReflectionTestUtils.setField(faq, "id", 1L);
            return faq;
        });

        FaqResponse response = faqService.createFaq(
                new FaqCreateRequest("초보자도 가입할 수 있나요?", "네. 초보자도 가입 가능합니다.")
        );

        ArgumentCaptor<Faq> captor = ArgumentCaptor.forClass(Faq.class);
        verify(faqRepository).save(captor.capture());
        assertThat(captor.getValue().getQuestion()).isEqualTo("초보자도 가입할 수 있나요?");
        assertThat(captor.getValue().getAnswer()).isEqualTo("네. 초보자도 가입 가능합니다.");
        assertThat(response.faqId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("FAQ 수정 시 질문과 답변을 변경한다")
    void updateFaqUpdatesFaq() {
        Faq faq = savedFaq(1L, "초보자도 가입할 수 있나요?", "네. 초보자도 가입 가능합니다.");
        when(faqRepository.findById(1L)).thenReturn(Optional.of(faq));

        FaqResponse response = faqService.updateFaq(
                1L,
                new FaqUpdateRequest("라켓이 없어도 되나요?", "네. 대여 라켓을 이용할 수 있습니다.")
        );

        assertThat(faq.getQuestion()).isEqualTo("라켓이 없어도 되나요?");
        assertThat(faq.getAnswer()).isEqualTo("네. 대여 라켓을 이용할 수 있습니다.");
        assertThat(response.faqId()).isEqualTo(1L);
        assertThat(response.question()).isEqualTo("라켓이 없어도 되나요?");
    }

    @Test
    @DisplayName("존재하지 않는 FAQ 수정 시 예외가 발생한다")
    void updateFaqThrowsWhenFaqDoesNotExist() {
        when(faqRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> faqService.updateFaq(
                999L,
                new FaqUpdateRequest("라켓이 없어도 되나요?", "네. 대여 라켓을 이용할 수 있습니다.")
        ))
                .isInstanceOf(YbcException.class)
                .hasMessage("존재하지 않는 FAQ입니다.");
    }

    @Test
    @DisplayName("FAQ 삭제 시 저장소에서 삭제한다")
    void deleteFaqDeletesFaq() {
        Faq faq = savedFaq(1L, "초보자도 가입할 수 있나요?", "네. 초보자도 가입 가능합니다.");
        when(faqRepository.findById(1L)).thenReturn(Optional.of(faq));

        faqService.deleteFaq(1L);

        verify(faqRepository).delete(faq);
    }

    private Faq savedFaq(Long id, String question, String answer) {
        Faq faq = Faq.create(question, answer);
        ReflectionTestUtils.setField(faq, "id", id);
        return faq;
    }
}
