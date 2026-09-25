package com.pistonpunk.gigahack26.repository.jpa;

import com.pistonpunk.gigahack26.domain.WorkspaceSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkspaceSettingsRepository extends JpaRepository<WorkspaceSettings, UUID> {
}
