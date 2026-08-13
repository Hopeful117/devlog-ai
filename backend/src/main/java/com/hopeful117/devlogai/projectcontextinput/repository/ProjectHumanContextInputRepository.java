package com.hopeful117.devlogai.projectcontextinput.repository;

import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInput;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectHumanContextInputRepository
        extends JpaRepository<ProjectHumanContextInput, UUID> {

    List<ProjectHumanContextInput> findByProject_IdOrderByUpdatedAtDescIdDesc(UUID projectId);

    List<ProjectHumanContextInput> findByProject_IdAndStatusOrderByUpdatedAtDescIdDesc(
            UUID projectId,
            ProjectHumanContextInputStatus status
    );

    Optional<ProjectHumanContextInput> findByIdAndProject_Id(UUID id, UUID projectId);
}
