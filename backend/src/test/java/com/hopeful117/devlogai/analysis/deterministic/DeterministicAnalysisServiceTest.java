package com.hopeful117.devlogai.analysis.deterministic;

import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeterministicAnalysisServiceTest {

    @Mock
    private FactRepository factRepository;

    @Mock
    private ObservationRepository observationRepository;

    @InjectMocks
    private DeterministicAnalysisServiceImpl deterministicAnalysisService;

    @Test
    void shouldReportPersistedDeterministicResults() {
        UUID analysisId = UUID.randomUUID();
        when(factRepository.countByAnalysisId(analysisId)).thenReturn(5L);
        when(observationRepository.countByAnalysisId(analysisId)).thenReturn(3L);

        DeterministicAnalysisResult result =
                deterministicAnalysisService.analyze(analysisId);

        assertEquals(5, result.factCount());
        assertEquals(3, result.observationCount());
    }
}
