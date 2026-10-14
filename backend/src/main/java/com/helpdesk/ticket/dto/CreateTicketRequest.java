package com.helpdesk.ticket.dto;

import com.helpdesk.ticket.TicketCategory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * category is optional (the customer's best guess; agents can change it).
 * customerEmail is ignored for customers (their own email is used).
 * Agents/admins creating a ticket on someone's behalf must provide it.
 */
public record CreateTicketRequest(
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 5000) String description,
        TicketCategory category,
        @Email String customerEmail
) {
    public CreateTicketRequest {
        if (customerEmail != null) customerEmail = customerEmail.strip().toLowerCase(Locale.ROOT);
    }
}
