package com.hopeful117.devlogai.repositorycontext.intelligence;

public record EvidencePrecisionPolicy(
        String key,
        String version,
        int maximumCommonTermPercentage,
        int minimumRelevanceScore,
        int maximumKindSharePercentage,
        int maximumCategorySharePercentage,
        int strongRelevanceScore
) {
    public static final EvidencePrecisionPolicy UNRESTRICTED =
            new EvidencePrecisionPolicy("unrestricted", "v1", 100, 0, 100, 100, 101);

    public EvidencePrecisionPolicy {
        if (key == null || key.isBlank() || version == null || version.isBlank()
                || maximumCommonTermPercentage < 1
                || maximumCommonTermPercentage > 100
                || minimumRelevanceScore < 0 || minimumRelevanceScore > 100
                || maximumKindSharePercentage < 1
                || maximumKindSharePercentage > 100
                || maximumCategorySharePercentage < 0
                || maximumCategorySharePercentage > 100
                || strongRelevanceScore < 0 || strongRelevanceScore > 101)
            throw new IllegalArgumentException("Evidence precision policy is invalid");
    }

    public static EvidencePrecisionPolicy compose(
            java.util.List<EvidencePrecisionPolicy> policies
    ) {
        if (policies.isEmpty()) return UNRESTRICTED;
        int common = policies.stream().mapToInt(
                EvidencePrecisionPolicy::maximumCommonTermPercentage).min().orElse(100);
        int minimum = policies.stream().mapToInt(
                EvidencePrecisionPolicy::minimumRelevanceScore).max().orElse(0);
        int share = policies.stream().mapToInt(
                EvidencePrecisionPolicy::maximumKindSharePercentage).min().orElse(100);
        int categoryShare = policies.stream().mapToInt(
                EvidencePrecisionPolicy::maximumCategorySharePercentage).min().orElse(100);
        int strong = policies.stream().mapToInt(
                EvidencePrecisionPolicy::strongRelevanceScore).min().orElse(101);
        String keys = policies.stream().map(value -> value.key() + ":" + value.version())
                .sorted().collect(java.util.stream.Collectors.joining("+"));
        return new EvidencePrecisionPolicy(keys, "composed-v1", common, minimum,
                share, categoryShare, strong);
    }
}
