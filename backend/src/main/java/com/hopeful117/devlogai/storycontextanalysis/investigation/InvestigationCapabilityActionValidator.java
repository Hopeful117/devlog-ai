package com.hopeful117.devlogai.storycontextanalysis.investigation;

import com.hopeful117.devlogai.shared.evidence.EvidencePathValidator;

import java.util.Map;
import java.util.Set;

/** Pure fail-closed authorization for Phase 1 investigation actions. */
public final class InvestigationCapabilityActionValidator {
    private InvestigationCapabilityActionValidator() { }

    public static InvestigationActionValidation validate(
            InvestigationAction action,
            InvestigationScope scope,
            InvestigationBudget budget,
            InvestigationUsage usage,
            Set<String> actionIds,
            Map<InvestigationActionKind, InvestigationCapability> capabilities) {
        if (action == null || scope == null || budget == null || usage == null || capabilities == null) {
            return reject("INVALID_INPUT", "Action, scope, budget, usage, and capabilities are required");
        }
        if (!validNonNegative(usage) || !validBudget(budget)) {
            return reject("INVALID_BUDGET", "Budget and usage values must be non-negative");
        }
        if (action.kind() == null || !capabilities.containsKey(action.kind())) {
            return reject("UNKNOWN_CAPABILITY", "Action capability is not authorized");
        }
        if (blank(action.actionId()) || action.actionId().length() > 100
                || !action.actionId().matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}")
                || actionIds != null && actionIds.contains(action.actionId())) {
            return reject("DUPLICATE_ACTION", "Action ID is missing or has already been used");
        }
        if (blank(scope.projectSlug()) || !scope.projectSlug().equals(action.projectSlug())) {
            return reject("PROJECT_OUT_OF_SCOPE", "Action project is outside the authorized scope");
        }
        if (blank(scope.revision()) || !scope.revision().equals(action.revision())) {
            return reject("REVISION_MISMATCH", "Action revision does not match the authorized revision");
        }
        if (blank(action.purpose()) || action.purpose().trim().length() > 500) {
            return reject("INVALID_PURPOSE", "Action purpose must contain at most 500 characters");
        }
        InvestigationCapability capability = capabilities.get(action.kind());
        if (capability == null || capability.kind() != action.kind()) {
            return reject("UNKNOWN_CAPABILITY", "Action capability is not authorized");
        }
        if (action.resultLimit() < 1 || action.resultLimit() > capability.maximumResultLimit()
                || action.maximumBytes() < 1 || action.maximumBytes() > capability.maximumBytes()
                || action.maximumTokens() < 1 || action.maximumTokens() > capability.maximumTokens()) {
            return reject("CAPABILITY_LIMIT_EXCEEDED", "Action limits exceed the capability policy");
        }
        if (exhausted(usage.steps(), budget.maximumSteps())
                || exhausted(usage.retrievalCalls(), budget.maximumRetrievalCalls())
                || exhausted(usage.evidenceItems(), budget.maximumEvidenceItems())
                || exhausted(usage.bytes(), budget.maximumBytes())
                || exhausted(usage.tokens(), budget.maximumTokens())
                || exhausted(usage.elapsedMillis(), budget.maximumElapsedMillis())
                || exhausted(usage.providerOutputTokens(), budget.maximumProviderOutputTokens())) {
            return reject("BUDGET_EXHAUSTED", "Investigation budget is exhausted");
        }
        if (exceeds(usage.steps(), 1, budget.maximumSteps())
                || exceeds(usage.retrievalCalls(), 1, budget.maximumRetrievalCalls())
                || exceeds(usage.evidenceItems(), action.resultLimit(), budget.maximumEvidenceItems())
                || exceeds(usage.bytes(), action.maximumBytes(), budget.maximumBytes())
                || exceeds(usage.tokens(), action.maximumTokens(), budget.maximumTokens())) {
            return reject("BUDGET_EXHAUSTED", "Action would exceed the remaining investigation budget");
        }
        if (action instanceof SearchEvidenceRequest search) {
            if (blank(search.query()) || search.query().length() > 500) {
                return reject("INVALID_LOCATOR", "Search query must contain between 1 and 500 characters");
            }
            return validatePath(search.pathPrefix(), scope);
        }
        if (action instanceof ReadEvidenceRangeRequest read) {
            if (!validPath(read.path()) || read.startLine() < 1 || read.endLine() < read.startLine()
                    || read.endLine() - read.startLine() + 1 > 500) {
                return reject("INVALID_LOCATOR", "Evidence range locator is invalid or unbounded");
            }
            return validatePath(read.path(), scope);
        }
        return reject("UNKNOWN_CAPABILITY", "Action type is not authorized");
    }

    private static InvestigationActionValidation validatePath(String path, InvestigationScope scope) {
        if (path == null) return InvestigationActionValidation.success();
        if (!validPath(path) || scope.allowedPathPrefixes().stream().noneMatch(prefix -> path.equals(prefix)
                || path.startsWith(prefix + "/"))) {
            return reject("PATH_OUT_OF_SCOPE", "Evidence path is outside the authorized scope");
        }
        return InvestigationActionValidation.success();
    }

    private static boolean validPath(String path) {
        return !blank(path) && EvidencePathValidator.isValidRelativePath(path)
                && !EvidencePathValidator.hasNonFileNamespacePrefix(path)
                && !path.contains("//") && path.indexOf('\\') < 0;
    }

    private static boolean validBudget(InvestigationBudget budget) {
        return budget.maximumSteps() > 0 && budget.maximumRetrievalCalls() > 0
                && budget.maximumEvidenceItems() > 0 && budget.maximumBytes() > 0
                && budget.maximumTokens() > 0 && budget.maximumElapsedMillis() > 0
                && budget.maximumProviderOutputTokens() > 0;
    }

    private static boolean validNonNegative(InvestigationUsage usage) {
        return usage.steps() >= 0 && usage.retrievalCalls() >= 0 && usage.evidenceItems() >= 0
                && usage.bytes() >= 0 && usage.tokens() >= 0 && usage.elapsedMillis() >= 0
                && usage.providerOutputTokens() >= 0;
    }

    private static boolean exhausted(long used, long maximum) {
        return used >= maximum;
    }

    private static boolean exceeds(long used, long requested, long maximum) {
        return used > maximum - requested;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static InvestigationActionValidation reject(String code, String message) {
        return InvestigationActionValidation.rejected(code, message);
    }
}
