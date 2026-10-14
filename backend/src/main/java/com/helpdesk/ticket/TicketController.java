package com.helpdesk.ticket;

import com.helpdesk.common.BadRequestException;
import com.helpdesk.common.CurrentUser;
import com.helpdesk.ticket.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicketRequest request,
                                                 @AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = CurrentUser.from(jwt);
        String email = user.isCustomer() ? user.email() : request.customerEmail();
        if (email == null) {
            throw new BadRequestException("customerEmail is required when staff create a ticket");
        }
        Ticket ticket = service.create(request.subject(), request.description(), request.category(), email);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(ticket.getId()).toUri();
        return ResponseEntity.created(location).body(TicketResponse.from(ticket));
    }

    /** Customers only ever see their own tickets; filters other than status are meant for agents. */
    @GetMapping
    public Page<TicketResponse> list(TicketFilter filter,
                                     @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                     Pageable pageable,
                                     @AuthenticationPrincipal Jwt jwt) {
        return service.list(filter, CurrentUser.from(jwt), pageable).map(TicketResponse::from);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public TicketStats stats() {
        return service.stats();
    }

    @GetMapping("/{id}")
    public TicketResponse get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return TicketResponse.from(service.getVisible(id, CurrentUser.from(jwt)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public TicketResponse update(@PathVariable Long id, @Valid @RequestBody UpdateTicketRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
        return TicketResponse.from(service.update(id, request, CurrentUser.from(jwt)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @GetMapping("/{id}/comments")
    public List<CommentResponse> comments(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.comments(id, CurrentUser.from(jwt)).stream().map(CommentResponse::from).toList();
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(@PathVariable Long id, @Valid @RequestBody CreateCommentRequest request,
                                      @AuthenticationPrincipal Jwt jwt) {
        return CommentResponse.from(service.addComment(id, request.body(), request.internal(), CurrentUser.from(jwt)));
    }
}
