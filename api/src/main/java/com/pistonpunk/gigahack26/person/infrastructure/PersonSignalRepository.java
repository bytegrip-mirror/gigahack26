package com.pistonpunk.gigahack26.person.infrastructure;

import com.pistonpunk.gigahack26.person.domain.PersonSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PersonSignalRepository extends JpaRepository<PersonSignal, UUID> {

    List<PersonSignal> findByPersonIdIn(Collection<UUID> personIds);

    @Modifying
    @Query("delete from PersonSignal s where s.personId in :personIds")
    void deleteByPersonIdIn(Collection<UUID> personIds);
}
