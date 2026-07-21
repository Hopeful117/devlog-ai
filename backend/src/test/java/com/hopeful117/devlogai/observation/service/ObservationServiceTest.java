package com.hopeful117.devlogai.observation.service;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.observation.dto.request.CreateObservationRequest;
import com.hopeful117.devlogai.observation.dto.response.ObservationResponse;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.entity.ObservationType;
import com.hopeful117.devlogai.observation.mapper.ObservationMapper;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ObservationServiceTest {

    @Mock
    ObservationRepository observationRepository;

    @Mock
    AnalysisRepository analysisRepository;

    @Mock
    FactRepository factRepository;

    @Mock
    ObservationMapper observationMapper;

    @InjectMocks
    ObservationServiceImpl observationService;

    @Test
    void shouldCreateObservationFromFactsOfSameAnalysis() {
        UUID analysisId = UUID.randomUUID();
        UUID factId = UUID.randomUUID();
        CreateObservationRequest request = CreateObservationRequest.builder()
                .analysisId(analysisId)
                .type(ObservationType.HTTP_SERVICE_COMMUNICATION)
                .content("Multiple services communicate through HTTP.")
                .supportingFactIds(Set.of(factId))
                .build();
        Analysis analysis = Analysis.builder().id(analysisId).build();
        Fact fact = Fact.builder().id(factId).analysis(analysis).build();
        Observation observation = new Observation();
        ObservationResponse response = mock(ObservationResponse.class);

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(factRepository.findAllById(request.getSupportingFactIds()))
                .thenReturn(List.of(fact));
        when(observationMapper.toEntity(request)).thenReturn(observation);
        when(observationRepository.save(observation)).thenReturn(observation);
        when(observationMapper.toResponse(observation)).thenReturn(response);

        ObservationResponse result = observationService.create(request);

        assertSame(response, result);
        assertSame(analysis, observation.getAnalysis());
        assertEquals(Set.of(fact), observation.getSupportingFacts());
        verify(observationRepository).save(observation);
    }

    @Test
    void shouldRejectObservationWhenAnalysisDoesNotExist() {
        UUID analysisId = UUID.randomUUID();
        CreateObservationRequest request = CreateObservationRequest.builder()
                .analysisId(analysisId)
                .build();

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> observationService.create(request));

        verifyNoInteractions(factRepository, observationMapper, observationRepository);
    }

    @Test
    void shouldRejectObservationWhenSupportingFactIsMissing() {
        UUID analysisId = UUID.randomUUID();
        CreateObservationRequest request = CreateObservationRequest.builder()
                .analysisId(analysisId)
                .supportingFactIds(Set.of(UUID.randomUUID()))
                .build();

        when(analysisRepository.findById(analysisId))
                .thenReturn(Optional.of(Analysis.builder().id(analysisId).build()));
        when(factRepository.findAllById(request.getSupportingFactIds()))
                .thenReturn(List.of());

        assertThrows(EntityNotFoundException.class, () -> observationService.create(request));

        verifyNoInteractions(observationMapper, observationRepository);
    }

    @Test
    void shouldRejectObservationWhenFactBelongsToAnotherAnalysis() {
        UUID analysisId = UUID.randomUUID();
        CreateObservationRequest request = CreateObservationRequest.builder()
                .analysisId(analysisId)
                .supportingFactIds(Set.of(UUID.randomUUID()))
                .build();
        Analysis analysis = Analysis.builder().id(analysisId).build();
        Fact fact = Fact.builder()
                .analysis(Analysis.builder().id(UUID.randomUUID()).build())
                .build();

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(factRepository.findAllById(request.getSupportingFactIds()))
                .thenReturn(List.of(fact));

        assertThrows(IllegalArgumentException.class, () -> observationService.create(request));

        verifyNoInteractions(observationMapper, observationRepository);
    }

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
