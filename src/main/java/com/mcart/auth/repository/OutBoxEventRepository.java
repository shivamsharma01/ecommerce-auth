package com.mcart.auth.repository;

import com.mcart.auth.entity.OutboxEventEntity;
import com.mcart.auth.entity.OutboxEventId;
import com.mcart.auth.model.EmailStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutBoxEventRepository extends JpaRepository<OutboxEventEntity, OutboxEventId> {

    List<OutboxEventEntity> findTop20ByStatusOrderByCreatedAtAsc(EmailStatus status);
}
