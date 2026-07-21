package com.hopeful117.devlogai.fact.service;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.fact.dto.request.CreateFactRequest;
import com.hopeful117.devlogai.fact.dto.response.FactResponse;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.fact.mapper.FactMapper;
import com.hopeful117.devlogai.fact.repository.FactRepository;
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
class FactServiceTest {

    @Mock
    FactRepository factRepository;

    @Mock
    AnalysisRepository analysisRepository;

    @Mock
    FactMapper factMapper;

    @InjectMocks
    FactServiceImpl factService;

    @Test
    void shouldCreateFactForAnalysis() {
        UUID analysisId = UUID.randomUUID();
        CreateFactRequest request = CreateFactRequest.builder()
                .analysisId(analysisId)
                .type(FactType.TECHNOLOGY)
                .content("The project uses PostgreSQL.")
                .source("pom.xml")
                .evidenceReferences(Set.of("backend/pom.xml"))
                .build();
        Analysis analysis = Analysis.builder().id(analysisId).build();
        Fact fact = new Fact();
        FactResponse response = mock(FactResponse.class);

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(factMapper.toEntity(request)).thenReturn(fact);
        when(factRepository.save(fact)).thenReturn(fact);
        when(factMapper.toResponse(fact)).thenReturn(response);

        FactResponse result = factService.create(request);

        assertSame(response, result);
        assertSame(analysis, fact.getAnalysis());
        verify(factRepository).save(fact);
    }

    @Test
    void shouldRejectFactWhenAnalysisDoesNotExist() {
        UUID analysisId = UUID.randomUUID();
        CreateFactRequest request = CreateFactRequest.builder()
                .analysisId(analysisId)
                .build();

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> factService.create(request));

        verifyNoInteractions(factMapper, factRepository);
    }

    @Test
    void shouldGetFactById() {
        UUID id = UUID.randomUUID();
        Fact fact = new Fact();
        FactResponse response = mock(FactResponse.class);

        when(factRepository.findById(id)).thenReturn(Optional.of(fact));
        when(factMapper.toResponse(fact)).thenReturn(response);

        assertSame(response, factService.getById(id));
    }

    @Test
    void shouldReturnFactsByAnalysis() {
        UUID analysisId = UUID.randomUUID();
        Fact fact = new Fact();
        FactResponse response = mock(FactResponse.class);

        when(factRepository.findByAnalysisIdOrderByDetectedAtDesc(analysisId))
                .thenReturn(List.of(fact));
        when(factMapper.toResponse(fact)).thenReturn(response);

        assertEquals(List.of(response), factService.getByAnalysis(analysisId));
    }
}
