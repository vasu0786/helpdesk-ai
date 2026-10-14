package com.helpdesk.ticket;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    @EntityGraph(attributePaths = "assignee")
    Optional<Ticket> findWithAssigneeById(Long id);

    @Query("""
            select t from Ticket t
            where t.slaBreached = false and t.status in :statuses and t.slaDueAt < :now
            """)
    List<Ticket> findNewlyBreached(Collection<TicketStatus> statuses, Instant now);

    long countByStatus(TicketStatus status);

    long countBySlaBreachedTrueAndStatusIn(Collection<TicketStatus> statuses);

    long countByAssigneeIsNullAndStatusIn(Collection<TicketStatus> statuses);
}
