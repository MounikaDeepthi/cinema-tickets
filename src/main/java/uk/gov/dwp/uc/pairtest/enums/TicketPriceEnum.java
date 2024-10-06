package uk.gov.dwp.uc.pairtest.enums;

import java.math.BigDecimal;

public enum TicketPriceEnum {
	ADULT(BigDecimal.valueOf(25)),
	CHILD(BigDecimal.valueOf(15)),
	INFANT(BigDecimal.valueOf(0));

	private final BigDecimal price;

	private TicketPriceEnum(BigDecimal price) {
		this.price= price;
	}


	public BigDecimal getPrice() {
		return price;
	}

}
