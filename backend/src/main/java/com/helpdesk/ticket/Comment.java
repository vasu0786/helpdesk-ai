package com.helpdesk.ticket;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "author_email", nullable = false)
    private String authorEmail;

    /** CUSTOMER, AGENT, ADMIN or SYSTEM (automatic notes such as SLA breaches). */
    @Column(name = "author_role", nullable = false)
    private String authorRole;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private boolean internal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Comment() {
    }

    public Comment(Long ticketId, Long authorId, String authorEmail, String authorRole, String body, boolean internal) {
        this.ticketId = ticketId;
        this.authorId = authorId;
        this.authorEmail = authorEmail;
        this.authorRole = authorRole;
        this.body = body;
        this.internal = internal;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getTicketId() { return ticketId; }
    public Long getAuthorId() { return authorId; }
    public String getAuthorEmail() { return authorEmail; }
    public String getAuthorRole() { return authorRole; }
    public String getBody() { return body; }
    public boolean isInternal() { return internal; }
    public Instant getCreatedAt() { return createdAt; }
}
