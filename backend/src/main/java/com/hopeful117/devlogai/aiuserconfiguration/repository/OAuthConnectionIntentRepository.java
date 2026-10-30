package com.hopeful117.devlogai.aiuserconfiguration.repository;

import com.hopeful117.devlogai.aiuserconfiguration.entity.OAuthConnectionIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.UUID;

public interface OAuthConnectionIntentRepository extends JpaRepository<OAuthConnectionIntent, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select intent from OAuthConnectionIntent intent where intent.id = :id")
    java.util.Optional<OAuthConnectionIntent> findByIdForUpdate(@Param("id") UUID id);
}
