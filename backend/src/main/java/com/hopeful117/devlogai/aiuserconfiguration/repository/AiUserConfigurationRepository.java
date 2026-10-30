package com.hopeful117.devlogai.aiuserconfiguration.repository;

import com.hopeful117.devlogai.aiuserconfiguration.entity.AiUserConfiguration;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.RepositoryDefinition;

import java.util.Optional;
import java.util.UUID;

@RepositoryDefinition(domainClass = AiUserConfiguration.class, idClass = UUID.class)
public interface AiUserConfigurationRepository extends Repository<AiUserConfiguration, UUID> {
    Optional<AiUserConfiguration> findByPrincipalId(String principalId);

    <S extends AiUserConfiguration> S save(S configuration);

    void delete(AiUserConfiguration configuration);
}
