package com.hopeful117.devlogai.analysis.deterministic;

import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeterministicAnalysisServiceImpl
        implements DeterministicAnalysisService {

    private final FactRepository factRepository;
    private final ObservationRepository observationRepository;

    @Override
    @Transactional(readOnly = true)
    public DeterministicAnalysisResult analyze(UUID analysisId) {
        return new DeterministicAnalysisResult(
                Math.toIntExact(factRepository.countByAnalysisId(analysisId)),
                Math.toIntExact(observationRepository.countByAnalysisId(analysisId))
        );
    }
}
