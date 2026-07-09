package com.ybc.ybc.faqs.service;

import com.ybc.ybc.faqs.domain.Faq;
import com.ybc.ybc.faqs.dto.request.FaqCreateRequest;
import com.ybc.ybc.faqs.dto.request.FaqUpdateRequest;
import com.ybc.ybc.faqs.dto.response.FaqListResponse;
import com.ybc.ybc.faqs.dto.response.FaqResponse;
import com.ybc.ybc.faqs.repository.FaqRepository;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqService {
    private final FaqRepository faqRepository;

    public FaqListResponse getFaqs() {
        List<Faq> faqs = faqRepository.findAllByOrderByIdAsc();
        if (faqs.isEmpty()) {
            throw new YbcException(ExceptionCode.FAQ_INFO_NOT_FOUND);
        }
        return new FaqListResponse(
                faqs.stream()
                        .map(FaqResponse::from)
                        .toList()
        );
    }

    @Transactional
    public FaqResponse createFaq(FaqCreateRequest request) {
        Faq faq = faqRepository.save(Faq.create(request.question(), request.answer()));
        return FaqResponse.from(faq);
    }

    @Transactional
    public FaqResponse updateFaq(Long faqId, FaqUpdateRequest request) {
        Faq faq = getFaqById(faqId);
        faq.update(request.question(), request.answer());
        return FaqResponse.from(faq);
    }

    @Transactional
    public void deleteFaq(Long faqId) {
        Faq faq = getFaqById(faqId);
        faqRepository.delete(faq);
    }

    private Faq getFaqById(Long faqId) {
        return faqRepository.findById(faqId)
                .orElseThrow(() -> new YbcException(ExceptionCode.FAQ_NOT_FOUND));
    }
}
