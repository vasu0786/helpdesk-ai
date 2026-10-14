package com.helpdesk.ticket;

import org.springframework.data.jpa.domain.Specification;

/** Building blocks for the ticket list filters. */
final class TicketSpecs {

    private TicketSpecs() {
    }

    static Specification<Ticket> customerEmail(String email) {
        return (root, q, cb) -> cb.equal(cb.lower(root.get("customerEmail")), email.toLowerCase());
    }

    static Specification<Ticket> status(TicketStatus status) {
        return (root, q, cb) -> cb.equal(root.get("status"), status);
    }

    static Specification<Ticket> category(TicketCategory category) {
        return (root, q, cb) -> cb.equal(root.get("category"), category);
    }

    static Specification<Ticket> priority(TicketPriority priority) {
        return (root, q, cb) -> cb.equal(root.get("priority"), priority);
    }

    static Specification<Ticket> assignee(Long assigneeId) {
        return (root, q, cb) -> cb.equal(root.get("assignee").get("id"), assigneeId);
    }

    static Specification<Ticket> unassigned() {
        return (root, q, cb) -> cb.isNull(root.get("assignee"));
    }

    static Specification<Ticket> breached(boolean breached) {
        return (root, q, cb) -> cb.equal(root.get("slaBreached"), breached);
    }

    /** Case-insensitive match on subject or customer email. */
    static Specification<Ticket> search(String text) {
        String like = "%" + text.toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, q, cb) -> cb.or(
                cb.like(cb.lower(root.get("subject")), like, '\\'),
                cb.like(cb.lower(root.get("customerEmail")), like, '\\'));
    }
}
