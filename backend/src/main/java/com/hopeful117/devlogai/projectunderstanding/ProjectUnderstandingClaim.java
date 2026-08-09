package com.hopeful117.devlogai.projectunderstanding;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.projectunderstanding.dto.ProjectUnderstandingOutcome;

record ProjectUnderstandingClaim(Analysis analysis, ProjectUnderstandingOutcome outcome) { }
