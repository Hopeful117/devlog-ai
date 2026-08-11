package com.hopeful117.devlogai.challenge.repository;

import com.hopeful117.devlogai.challenge.entity.Challenge;
import com.hopeful117.devlogai.challenge.entity.ChallengeStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChallengeRepository extends JpaRepository<Challenge, UUID> {

    List<Challenge> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<Challenge> findByProjectIdOrderByCreatedAtDescIdDesc(
            UUID projectId,
            Pageable pageable
    );

    List<Challenge> findByProjectIdAndStatusOrderByCreatedAtDesc(
            UUID projectId,
            ChallengeStatus status
    );
}
