package com.helpdesk.ticket;

import com.helpdesk.common.BadRequestException;
import com.helpdesk.common.ConflictException;
import com.helpdesk.common.CurrentUser;
import com.helpdesk.common.ForbiddenException;
import com.helpdesk.common.NotFoundException;
import com.helpdesk.sla.SlaPolicy;
import com.helpdesk.ticket.dto.TicketStats;
import com.helpdesk.ticket.dto.UpdateTicketRequest;
import com.helpdesk.user.Role;
import com.helpdesk.user.User;
import com.helpdesk.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Service
public class TicketService {

    private static final Set<TicketStatus> NOT_DONE =
            EnumSet.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.WAITING_ON_CUSTOMER);

    private final TicketRepository tickets;
    private final CommentRepository comments;
    private final UserRepository users;
    private final SlaPolicy slaPolicy;

    public TicketService(TicketRepository tickets, CommentRepository comments, UserRepository users,
                         SlaPolicy slaPolicy) {
        this.tickets = tickets;
        this.comments = comments;
        this.users = users;
        this.slaPolicy = slaPolicy;
    }

    // ---------- create ----------

    @Transactional
    public Ticket create(String subject, String description, TicketCategory category, String customerEmail) {
        Ticket t = new Ticket();
        t.setSubject(subject.strip());
        t.setDescription(description.strip());
        t.setCategory(category);
        t.setCustomerEmail(customerEmail);
        t.setCreatedAt(Instant.now());
        t.setPriority(TicketPriority.MEDIUM); // agents raise or lower it when they review the ticket
        t.setSlaDueAt(slaPolicy.dueAt(t.getCreatedAt(), t.getPriority()));
        return tickets.save(t);
    }

    // ---------- read ----------

    /** Loads a ticket the caller is allowed to see. Customers get 404 for other people's tickets. */
    @Transactional(readOnly = true)
    public Ticket getVisible(Long id, CurrentUser user) {
        Ticket t = tickets.findWithAssigneeById(id).orElseThrow(() -> notFound(id));
        if (user.isCustomer() && !t.getCustomerEmail().equalsIgnoreCase(user.email())) {
            throw notFound(id);
        }
        return t;
    }

    @Transactional(readOnly = true)
    public Page<Ticket> list(TicketFilter f, CurrentUser user, Pageable pageable) {
        Specification<Ticket> spec = Specification.allOf();
        if (user.isCustomer()) spec = spec.and(TicketSpecs.customerEmail(user.email()));
        if (f.status() != null) spec = spec.and(TicketSpecs.status(f.status()));
        if (f.category() != null) spec = spec.and(TicketSpecs.category(f.category()));
        if (f.priority() != null) spec = spec.and(TicketSpecs.priority(f.priority()));
        if (f.assigneeId() != null) spec = spec.and(TicketSpecs.assignee(f.assigneeId()));
        if (Boolean.TRUE.equals(f.unassigned())) spec = spec.and(TicketSpecs.unassigned());
        if (f.breached() != null) spec = spec.and(TicketSpecs.breached(f.breached()));
        if (StringUtils.hasText(f.q())) spec = spec.and(TicketSpecs.search(f.q().strip()));
        Page<Ticket> page = tickets.findAll(spec, pageable);
        page.forEach(t -> { if (t.getAssignee() != null) t.getAssignee().getFullName(); }); // load before tx ends
        return page;
    }

    @Transactional(readOnly = true)
    public TicketStats stats() {
        return new TicketStats(
                tickets.countByStatus(TicketStatus.OPEN),
                tickets.countByStatus(TicketStatus.IN_PROGRESS),
                tickets.countByStatus(TicketStatus.WAITING_ON_CUSTOMER),
                tickets.countByStatus(TicketStatus.RESOLVED),
                tickets.countByStatus(TicketStatus.CLOSED),
                tickets.countBySlaBreachedTrueAndStatusIn(NOT_DONE),
                tickets.countByAssigneeIsNullAndStatusIn(NOT_DONE));
    }

    // ---------- update (agents / admins) ----------

    @Transactional
    public Ticket update(Long id, UpdateTicketRequest req, CurrentUser actor) {
        Ticket t = tickets.findWithAssigneeById(id).orElseThrow(() -> notFound(id));

        if (Boolean.TRUE.equals(req.unassign()) && req.assigneeId() != null) {
            throw new BadRequestException("Send either assigneeId or unassign, not both");
        }
        if (req.assigneeId() != null) {
            t.setAssignee(findAssignableUser(req.assigneeId()));
        } else if (Boolean.TRUE.equals(req.unassign())) {
            t.setAssignee(null);
        }

        if (req.category() != null) {
            t.setCategory(req.category());
        }
        if (req.priority() != null && req.priority() != t.getPriority()) {
            t.setPriority(req.priority());
            t.setSlaDueAt(slaPolicy.dueAt(t.getCreatedAt(), req.priority()));
        }

        if (req.status() != null && req.status() != t.getStatus()) {
            changeStatus(t, req.status());
            // Picking up an unassigned ticket assigns it to whoever picked it up.
            if (req.status() == TicketStatus.IN_PROGRESS && t.getAssignee() == null) {
                t.setAssignee(users.findById(actor.id()).orElseThrow());
            }
        }
        return t;
    }

    @Transactional
    public void delete(Long id) {
        tickets.delete(tickets.findById(id).orElseThrow(() -> notFound(id)));
    }

    // ---------- comments ----------

    @Transactional(readOnly = true)
    public java.util.List<Comment> comments(Long ticketId, CurrentUser user) {
        getVisible(ticketId, user);
        return user.isCustomer()
                ? comments.findByTicketIdAndInternalFalseOrderByCreatedAtAsc(ticketId)
                : comments.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    @Transactional
    public Comment addComment(Long ticketId, String body, boolean internal, CurrentUser user) {
        Ticket t = getVisible(ticketId, user);
        if (t.getStatus() == TicketStatus.CLOSED) {
            throw new ConflictException("Ticket " + ticketId + " is closed");
        }
        if (internal && user.isCustomer()) {
            throw new ForbiddenException("Customers cannot add internal notes");
        }
        // A customer replying brings the ticket back to the agents.
        if (user.isCustomer() && (t.getStatus() == TicketStatus.WAITING_ON_CUSTOMER
                || t.getStatus() == TicketStatus.RESOLVED)) {
            changeStatus(t, TicketStatus.IN_PROGRESS);
        }
        return comments.save(new Comment(ticketId, user.id(), user.email(), user.role().name(), body.strip(), internal));
    }

    @Transactional
    public void addSystemNote(Long ticketId, String body) {
        comments.save(new Comment(ticketId, null, "system", "SYSTEM", body, true));
    }

    // ---------- helpers ----------

    private void changeStatus(Ticket t, TicketStatus next) {
        if (!t.getStatus().canMoveTo(next)) {
            throw new ConflictException("Cannot move ticket from " + t.getStatus() + " to " + next
                    + ". Allowed: " + t.getStatus().allowedNext());
        }
        Instant now = Instant.now();
        switch (next) {
            case RESOLVED -> t.setResolvedAt(now);
            case CLOSED -> t.setClosedAt(now);
            case IN_PROGRESS -> t.setResolvedAt(null); // reopened
            default -> { }
        }
        t.setStatus(next);
    }

    private User findAssignableUser(Long userId) {
        User u = users.findById(userId)
                .orElseThrow(() -> new BadRequestException("Assignee " + userId + " does not exist"));
        if (u.getRole() == Role.CUSTOMER || !u.isActive()) {
            throw new BadRequestException("Tickets can only be assigned to active agents or admins");
        }
        return u;
    }

    private static NotFoundException notFound(Long id) {
        return new NotFoundException("Ticket " + id + " not found");
    }
}
