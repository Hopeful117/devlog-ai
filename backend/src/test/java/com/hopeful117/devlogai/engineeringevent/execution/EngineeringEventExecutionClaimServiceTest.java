package com.hopeful117.devlogai.engineeringevent.execution;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.engineeringevent.AnalysisEvolutionScope;
import com.hopeful117.devlogai.engineeringevent.AnalysisEvolutionScopeRepository;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EngineeringEventExecutionClaimServiceTest {
    @Test
    void returnsTheExistingActiveExecutionForTheSameDeterministicKey() {
        AnalysisRepository analyses = mock(AnalysisRepository.class);
        AnalysisEvolutionScopeRepository scopes = mock(AnalysisEvolutionScopeRepository.class);
        EngineeringEventExecutionKey keys = mock(EngineeringEventExecutionKey.class);
        PreparedEngineeringEventExecution prepared = mock(PreparedEngineeringEventExecution.class);
        Analysis existing = Analysis.builder().id(UUID.randomUUID())
                .status(AnalysisStatus.IN_PROGRESS).build();
        AnalysisEvolutionScope scope = mock(AnalysisEvolutionScope.class);
        when(keys.compute(prepared)).thenReturn("execution-key");
        when(analyses.findByEvolutionExecutionKeyAndStatusIn(
                org.mockito.ArgumentMatchers.eq("execution-key"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(existing));
        when(scopes.findById(existing.getId())).thenReturn(Optional.of(scope));
        var service = new EngineeringEventExecutionClaimService(analyses, scopes,
                mock(ProjectRepository.class), mock(SourceRepository.class), keys, mock(ObjectMapper.class));

        EngineeringEventExecutionClaimService.Claim claim = service.claim(prepared);

        assertSame(existing, claim.analysis());
        assertSame(scope, claim.scope());
        assertFalse(claim.created());
    }
}
