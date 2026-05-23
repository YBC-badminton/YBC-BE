package com.ybc.ybc.interviews.domain;

import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.applications.domain.InterviewTime;
import com.ybc.ybc.applications.domain.InterviewTimeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "interview",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_interview_application_id",
                columnNames = "application_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Interview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private Application application;

    @Convert(converter = InterviewTimeConverter.class)
    @Column(name = "scheduled_at")
    private InterviewTime scheduledAt;

    @Column(length = 255)
    private String memo;

    @Builder
    private Interview(Application application, InterviewTime scheduledAt, String memo) {
        this.application = application;
        this.scheduledAt = scheduledAt;
        this.memo = memo;
    }

    public static Interview create(Application application, InterviewTime scheduledAt, String memo) {
        return Interview.builder()
                .application(application)
                .scheduledAt(scheduledAt)
                .memo(memo)
                .build();
    }

    public void update(InterviewTime scheduledAt, String memo) {
        this.scheduledAt = scheduledAt;
        this.memo = memo;
    }

    public Long getApplicationId() {
        return application.getId();
    }
}
