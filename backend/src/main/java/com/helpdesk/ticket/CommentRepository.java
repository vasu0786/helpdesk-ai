package com.helpdesk.ticket;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    List<Comment> findByTicketIdAndInternalFalseOrderByCreatedAtAsc(Long ticketId);
}
