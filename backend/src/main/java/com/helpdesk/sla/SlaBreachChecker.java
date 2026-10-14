package com.helpdesk.sla;

import com.helpdesk.ticket.TicketRepository;
import com.helpdesk.ticket.TicketService;
import com.helpdesk.ticket.TicketStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Every check-interval, finds OPEN / IN_PROGRESS tickets past their SLA deadline,
 * marks them breached and adds an internal note.
 * Tickets WAITING_ON_CUSTOMER are not flagged while waiting.
 */
@Component
public class SlaBreachChecker {

    private static final Logger log = LoggerFactory.getLogger(SlaBreachChecker.class);

    private final TicketRepository tickets;
    private final TicketService ticketService;

    public SlaBreachChecker(TicketRepository tickets, TicketService ticketService) {
        this.tickets = tickets;
        this.ticketService = ticketService;
    }

    @Scheduled(fixedDelayString = "${helpdesk.sla.check-interval}", initialDelayString = "PT10S")
    @Transactional
    public void checkBreaches() {
        Instant now = Instant.now();
        var breached = tickets.findNewlyBreached(TicketStatus.SLA_ACTIVE, now);
        breached.forEach(t -> {
            t.setSlaBreached(true);
            t.setSlaBreachedAt(now);
            ticketService.addSystemNote(t.getId(), "SLA breached: was due " + t.getSlaDueAt()
                    + " (priority " + t.getPriority() + ").");
        });
        if (!breached.isEmpty()) {
            log.warn("{} ticket(s) breached their SLA", breached.size());
        }
    }
}
