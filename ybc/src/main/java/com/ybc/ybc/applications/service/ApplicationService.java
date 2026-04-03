package com.ybc.ybc.applications.service;

import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.applications.dto.request.ApplicationRequest;
import com.ybc.ybc.applications.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplicationService {
    private final ApplicationRepository applicationRepository;

    // 지원서 저장
    @Transactional
    public void createApplication(ApplicationRequest request){
        Application application = request.toApplication();
        applicationRepository.save(application);
    }
}
