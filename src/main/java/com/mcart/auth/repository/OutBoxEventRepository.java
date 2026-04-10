package com.mcart.auth.repository;

import com.mcart.auth.entity.OutboxEventEntity;
import com.mcart.auth.entity.OutboxEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutBoxEventRepository extends JpaRepository<OutboxEventEntity, OutboxEventId> {

    /**
     * Claims up to 20 pending rows for processing. {@code FOR UPDATE SKIP LOCKED} ensures only one
     * auth replica handles each row when running multiple pods against PostgreSQL.
     */
    @Query(
            value = """
                    SELECT * FROM outbox_event
                    WHERE status = :status
                    ORDER BY created_at ASC
                    LIMIT 20
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    List<OutboxEventEntity> findPendingForPublish(@Param("status") String status);
}
