package com.hopeful117.devlogai.observation.service;

import com.hopeful117.devlogai.observation.dto.response.ObservationResponse;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.mapper.ObservationMapper;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ObservationServiceTest {

    @Mock
    ObservationRepository observationRepository;

    @Mock
    ObservationMapper observationMapper;

    @InjectMocks
    ObservationServiceImpl observationService;

    @Test
    void shouldReturnObservationsByAnalysis() {
        UUID analysisId = UUID.randomUUID();
        Observation observation = new Observation();
        ObservationResponse response = mock(ObservationResponse.class);

        when(observationRepository.findByAnalysisIdOrderByCreatedAtDesc(analysisId))
                .thenReturn(List.of(observation));
        when(observationMapper.toResponse(observation)).thenReturn(response);

        assertEquals(List.of(response), observationService.getByAnalysis(analysisId));
    }
}
