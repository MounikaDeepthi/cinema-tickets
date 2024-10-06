package uk.gov.dwp.uc.pairtest.enums;

public enum ErrorMessages {
	INVALID_ACCOUNT("Invalid Account ID: %s."),
	EMPTY_REQUEST("Invalid Ticket Requests."),
	MAX_LIMIT_EXCEDED("Maximum purchasable ticket limit %s exceeded."),
	NO_ADULT_ACCOMPANY("Child and Infant tickets cannot be purchased without purchasing an Adult ticket."),
	PRICE_UNDEFINED("Price is not defined for ticket type: %s.");

	private String message;

	private ErrorMessages(String message) {
		this.message= message;
	}

	public String getMessage() {
		return message;
	}
}
