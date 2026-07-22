package com.hopeful117.devlogai.insight.service;

import com.hopeful117.devlogai.insight.dto.response.InsightResponse;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.mapper.InsightMapper;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsightServiceTest {
    @Mock InsightRepository repository;
    @Mock InsightMapper mapper;
    @InjectMocks InsightServiceImpl service;

    @Test
    void shouldReturnInsightById() {
        UUID id = UUID.randomUUID();
        Insight insight = new Insight();
        InsightResponse response = mock(InsightResponse.class);
        when(repository.findById(id)).thenReturn(Optional.of(insight));
        when(mapper.toResponse(insight)).thenReturn(response);
        assertSame(response, service.getById(id));
    }

    @Test
    void shouldRejectUnknownInsight() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.getById(id));
    }

    @Test
    void shouldFilterInsights() {
        UUID projectId = UUID.randomUUID();
        Insight insight = new Insight();
        InsightResponse response = mock(InsightResponse.class);
        when(mapper.toResponse(insight)).thenReturn(response);
        when(repository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(insight));
        when(repository.findByProjectIdAndTypeOrderByCreatedAtDesc(projectId, InsightType.ARCHITECTURAL))
                .thenReturn(List.of(insight));
        when(repository.findByProjectIdAndSeverityOrderByCreatedAtDesc(projectId, InsightSeverity.CRITICAL))
                .thenReturn(List.of(insight));
        when(repository.findByProjectIdAndTypeAndSeverityOrderByCreatedAtDesc(
                projectId, InsightType.ARCHITECTURAL, InsightSeverity.CRITICAL)).thenReturn(List.of(insight));

        assertEquals(List.of(response), service.getByProject(projectId));
        assertEquals(List.of(response), service.getByProjectAndType(projectId, InsightType.ARCHITECTURAL));
        assertEquals(List.of(response), service.getByProjectAndSeverity(projectId, InsightSeverity.CRITICAL));
        assertEquals(List.of(response), service.getByProjectAndTypeAndSeverity(
                projectId, InsightType.ARCHITECTURAL, InsightSeverity.CRITICAL));
    }
}
