package com.helpdesk.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** internal=true makes an agent-only note that customers never see. */
public record CreateCommentRequest(
        @NotBlank @Size(max = 5000) String body,
        boolean internal
) {
}
