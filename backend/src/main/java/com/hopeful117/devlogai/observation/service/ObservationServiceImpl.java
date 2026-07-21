package com.hopeful117.devlogai.observation.service;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.observation.dto.request.CreateObservationRequest;
import com.hopeful117.devlogai.observation.dto.response.ObservationResponse;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.mapper.ObservationMapper;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ObservationServiceImpl implements ObservationService {

    private final ObservationRepository observationRepository;
    private final AnalysisRepository analysisRepository;
    private final FactRepository factRepository;
    private final ObservationMapper observationMapper;

    @Override
    public ObservationResponse create(CreateObservationRequest request) {
        Analysis analysis = analysisRepository.findById(request.getAnalysisId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Analysis", request.getAnalysisId()
                ));

        List<Fact> facts = factRepository.findAllById(request.getSupportingFactIds());
        if (facts.size() != request.getSupportingFactIds().size()) {
            throw new EntityNotFoundException("Fact", "supportingFactIds");
        }

        if (facts.stream().anyMatch(fact ->
                !fact.getAnalysis().getId().equals(analysis.getId()))) {
            throw new IllegalArgumentException(
                    "Supporting facts must belong to the specified analysis"
            );
        }

        Observation observation = observationMapper.toEntity(request);
        observation.setAnalysis(analysis);
        observation.setSupportingFacts(new LinkedHashSet<>(facts));

        return observationMapper.toResponse(
                observationRepository.save(observation)
        );
    }

    @Override
    public ObservationResponse getById(UUID id) {
        return observationRepository.findById(id)
                .map(observationMapper::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Observation", id));
    }

    @Override
    public List<ObservationResponse> getByAnalysis(UUID analysisId) {
        return observationRepository.findByAnalysisIdOrderByCreatedAtDesc(analysisId)
                .stream()
                .map(observationMapper::toResponse)
                .toList();
    }
}
