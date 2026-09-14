package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.contracts.engineeringcontext.TrustTier;
import com.hopeful117.devlogai.knowledge.relation.entity.EntityType;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Consumer-neutral relationship projection. It is reconstructed context data,
 * not a persistence model and not a replacement for KnowledgeRelation.
 */
public record EngineeringRelationship(
        String id,
        Endpoint source,
        String relationType,
        Endpoint target,
        Origin origin,
        TrustTier trustTier,
        String revision,
        List<String> evidenceReferences
) {
    public EngineeringRelationship {
        Objects.requireNonNull(id);
        Objects.requireNonNull(relationType);
        Objects.requireNonNull(origin);
        Objects.requireNonNull(trustTier);
        evidenceReferences = evidenceReferences == null ? List.of() : List.copyOf(evidenceReferences);
    }

    public static EngineeringRelationship fromKnowledge(
            ProjectContextSnapshot.KnowledgeRelationSnapshot relation) {
        return new EngineeringRelationship(
                relation.id().toString(),
                new KnowledgeEndpoint(relation.sourceEntityType(), relation.sourceEntityId()),
                relation.relationType().name(),
                new KnowledgeEndpoint(relation.targetEntityType(), relation.targetEntityId()),
                Origin.DURABLE_KNOWLEDGE,
                TrustTier.TRUSTED,
                null,
                List.of());
    }

    public enum Origin {
        DURABLE_KNOWLEDGE,
        REPOSITORY_DERIVED
    }

    public sealed interface Endpoint
            permits KnowledgeEndpoint, RepositoryCommitEndpoint, RepositoryFileEndpoint {
        String kind();

        String canonicalIdentity();
    }

    public record KnowledgeEndpoint(EntityType entityType, UUID entityId) implements Endpoint {
        public KnowledgeEndpoint {
            Objects.requireNonNull(entityType);
            Objects.requireNonNull(entityId);
        }

        @Override
        public String kind() {
            return entityType.name();
        }

        @Override
        public String canonicalIdentity() {
            return entityType.name() + ":" + entityId;
        }
    }

    public record RepositoryCommitEndpoint(UUID projectId, UUID sourceId, String commitHash)
            implements Endpoint {
        public RepositoryCommitEndpoint {
            Objects.requireNonNull(projectId);
            Objects.requireNonNull(sourceId);
            Objects.requireNonNull(commitHash);
        }

        @Override
        public String kind() {
            return "COMMIT";
        }

        @Override
        public String canonicalIdentity() {
            return "commit:" + projectId + ":" + sourceId + ":" + commitHash;
        }
    }

    public record RepositoryFileEndpoint(
            UUID projectId, UUID sourceId, String revision, String path) implements Endpoint {
        public RepositoryFileEndpoint {
            Objects.requireNonNull(projectId);
            Objects.requireNonNull(sourceId);
            Objects.requireNonNull(revision);
            Objects.requireNonNull(path);
        }

        @Override
        public String kind() {
            return "FILE";
        }

        @Override
        public String canonicalIdentity() {
            return "file:" + projectId + ":" + sourceId + ":" + revision + ":" + path;
        }
    }
}
