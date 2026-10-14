package com.helpdesk.ticket;

import com.helpdesk.user.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.OPEN;

    @Enumerated(EnumType.STRING)
    private TicketCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketPriority priority = TicketPriority.MEDIUM;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Column(name = "sla_due_at", nullable = false)
    private Instant slaDueAt;

    @Column(name = "sla_breached", nullable = false)
    private boolean slaBreached;

    @Column(name = "sla_breached_at")
    private Instant slaBreachedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }
    public TicketCategory getCategory() { return category; }
    public void setCategory(TicketCategory category) { this.category = category; }
    public TicketPriority getPriority() { return priority; }
    public void setPriority(TicketPriority priority) { this.priority = priority; }
    public User getAssignee() { return assignee; }
    public void setAssignee(User assignee) { this.assignee = assignee; }
    public Instant getSlaDueAt() { return slaDueAt; }
    public void setSlaDueAt(Instant slaDueAt) { this.slaDueAt = slaDueAt; }
    public boolean isSlaBreached() { return slaBreached; }
    public void setSlaBreached(boolean slaBreached) { this.slaBreached = slaBreached; }
    public Instant getSlaBreachedAt() { return slaBreachedAt; }
    public void setSlaBreachedAt(Instant slaBreachedAt) { this.slaBreachedAt = slaBreachedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
