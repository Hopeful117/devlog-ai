package com.hopeful117.devlogai.observation.service;

import com.hopeful117.devlogai.observation.dto.response.ObservationResponse;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.mapper.ObservationMapper;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ObservationServiceImpl implements ObservationService {

    private final ObservationRepository observationRepository;
    private final ObservationMapper observationMapper;

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
