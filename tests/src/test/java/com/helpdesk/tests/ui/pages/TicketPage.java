package com.helpdesk.tests.ui.pages;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;

public class TicketPage extends BasePage {

    private final By createdMessage = byTestId("ticket-created-message");
    private final By subject = byTestId("ticket-subject");
    private final By customer = byTestId("ticket-customer");
    private final By replyBox = byTestId("reply-body");
    private final By actionsPanel = byTestId("actions-panel");        // status, priority, category, assignee: staff only
    private final By internalNote = byTestId("reply-internal");       // "Internal note" tick box: staff only
    private final By deleteButton = byTestId("delete-ticket");        // admin only
    private final By ticketId = byTestId("ticket-id");
    private final By error = byTestId("ticket-error");
    private final By status = byTestId("ticket-status");
    private final By priority = byTestId("ticket-priority");
    private final By assignee = byTestId("ticket-assignee");
    private final By comments = byTestId("comments");
    private final By commentBody = byTestId("comment-body");
    private final By replySubmit = byTestId("reply-submit");
    private final By closedNote = byTestId("closed-note");
    private final By updateStatus = byTestId("update-status");
    private final By updateStatusOptions = By.cssSelector("[data-testid='update-status'] option");
    private final By updatePriority = byTestId("update-priority");
    private final By updateAssignee = byTestId("update-assignee");
    private final By updateSubmit = byTestId("update-submit");
    private final By updateSuccess = byTestId("update-success");

    public TicketPage(WebDriver driver) {
        super(driver);
    }

    public void openTicket(String id) {
        open("/ticket.html?id=" + id);
    }

    /** The page shows "#123"; this returns "123" */
    public String getTicketId() {
        return getText(ticketId).replace("#", "");
    }

    /** The red message shown when the ticket cannot be opened */
    public String getErrorMessage() {
        return getText(error);
    }

    public String getStatus() {
        return getText(status);
    }

    public String getPriority() {
        return getText(priority);
    }

    public String getAssignee() {
        return getText(assignee);
    }

    // ---------- replies ----------

    public void reply(String body) {
        type(replyBox, body);
        click(replySubmit);
        wait.until(ExpectedConditions.textToBePresentInElementLocated(comments, body));   // the new reply is on the page
    }

    public void addInternalNote(String body) {
        type(replyBox, body);
        click(internalNote);
        click(replySubmit);
        wait.until(ExpectedConditions.textToBePresentInElementLocated(comments, body));
    }

    public List<String> getCommentBodies() {
        waitForVisible(subject);      // the ticket has loaded
        return getTexts(commentBody);
    }

    public boolean isClosedNoteDisplayed() {
        waitForVisible(subject);
        return isDisplayed(closedNote);
    }

    // ---------- the actions panel (agents and admins) ----------

    public List<String> getStatusOptions() {
        waitForVisible(updateStatus);
        return getTexts(updateStatusOptions);
    }

    public void selectStatus(String statusText) {
        selectByText(updateStatus, statusText);
    }

    public void selectPriority(String priorityText) {
        selectByText(updatePriority, priorityText);
    }

    public void selectAssignee(String assigneeText) {
        selectByText(updateAssignee, assigneeText);
    }

    public void saveChanges() {
        click(updateSubmit);
        waitForVisible(updateSuccess);      // the green "Saved." message
    }

    /** Clicks Delete ticket and presses OK in the browser's confirm box */
    public void deleteTicket() {
        click(deleteButton);
        wait.until(ExpectedConditions.alertIsPresent()).accept();
    }

    public String getCreatedMessage() {
        return getText(createdMessage);
    }

    public String getSubject() {
        return getText(subject);
    }

    public String getCustomer() {
        return getText(customer);
    }

    public boolean isReplyBoxDisplayed() {
        waitForVisible(subject);      // the ticket has loaded
        return isDisplayed(replyBox);
    }

    public boolean isActionsPanelDisplayed() {
        waitForVisible(subject);
        return isDisplayed(actionsPanel);
    }

    public boolean isInternalNoteOptionDisplayed() {
        waitForVisible(subject);
        return isDisplayed(internalNote);
    }

    public boolean isDeleteButtonDisplayed() {
        waitForVisible(subject);
        return isDisplayed(deleteButton);
    }

    //Click on My tickets from nav bar
    public void clickMyTicket()
    {
        clickNavLink("my-tickets");
    }
}
