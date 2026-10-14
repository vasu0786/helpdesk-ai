package com.helpdesk.ticket.dto;

import com.helpdesk.ticket.Comment;

import java.time.Instant;

public record CommentResponse(Long id, Long ticketId, String authorEmail, String authorRole,
                              String body, boolean internal, Instant createdAt) {

    public static CommentResponse from(Comment c) {
        return new CommentResponse(c.getId(), c.getTicketId(), c.getAuthorEmail(), c.getAuthorRole(),
                c.getBody(), c.isInternal(), c.getCreatedAt());
    }
}
