package com.pistonpunk.gigahack26.settings.infrastructure;

import com.pistonpunk.gigahack26.settings.domain.WorkspaceSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkspaceSettingsRepository extends JpaRepository<WorkspaceSettings, UUID> {
}
