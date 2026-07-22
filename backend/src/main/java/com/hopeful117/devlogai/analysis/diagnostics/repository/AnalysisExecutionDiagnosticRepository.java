package com.hopeful117.devlogai.analysis.diagnostics.repository;

import com.hopeful117.devlogai.analysis.diagnostics.entity.AnalysisExecutionDiagnostic;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AnalysisExecutionDiagnosticRepository extends JpaRepository<AnalysisExecutionDiagnostic, UUID> {
}
