package com.hopeful117.devlogai.fact.service;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.fact.dto.request.CreateFactRequest;
import com.hopeful117.devlogai.fact.dto.response.FactResponse;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.mapper.FactMapper;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FactServiceImpl implements FactService {

    private final FactRepository factRepository;
    private final AnalysisRepository analysisRepository;
    private final FactMapper factMapper;

    @Override
    public FactResponse create(CreateFactRequest request) {
        Analysis analysis = analysisRepository.findById(request.getAnalysisId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Analysis", request.getAnalysisId()
                ));

        Fact fact = factMapper.toEntity(request);
        fact.setAnalysis(analysis);

        return factMapper.toResponse(factRepository.save(fact));
    }

    @Override
    public FactResponse getById(UUID id) {
        return factRepository.findById(id)
                .map(factMapper::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Fact", id));
    }

    @Override
    public List<FactResponse> getByAnalysis(UUID analysisId) {
        return factRepository.findByAnalysisIdOrderByDetectedAtDesc(analysisId)
                .stream()
                .map(factMapper::toResponse)
                .toList();
    }
}
