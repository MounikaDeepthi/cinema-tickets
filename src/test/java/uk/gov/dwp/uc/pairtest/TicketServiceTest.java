package uk.gov.dwp.uc.pairtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;
import uk.gov.dwp.uc.pairtest.enums.TicketPriceEnum;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

public class TicketServiceTest {

	@Mock
	private SeatReservationService seatReservationService;

	@Mock
	private TicketPaymentService ticketPaymentService;

	private TicketService ticketService;

	@Before
	public void init() {
		MockitoAnnotations.openMocks(this);
		ticketService = new TicketServiceImpl(seatReservationService, ticketPaymentService);
	}

	@Test
	public void purchaseTicketsSuccess() {
		doNothing().when(seatReservationService).reserveSeat(Mockito.anyLong(), Mockito.anyInt());
		doNothing().when(ticketPaymentService).makePayment(Mockito.anyLong(), Mockito.anyInt());
		ticketService.purchaseTickets(6L, 
				new TicketTypeRequest(Type.INFANT, 1),
				new TicketTypeRequest(Type.CHILD, 2),
				new TicketTypeRequest(Type.ADULT, 5));
		verify(ticketPaymentService).makePayment(6L, 155);
		verify(seatReservationService).reserveSeat(6L, 7);
	}

	@Test
	public void purchaseTicketswithInvalidAccount() {
		InvalidPurchaseException exception = assertThrows(InvalidPurchaseException.class, 
				() -> ticketService.purchaseTickets(0L));
		assertEquals("Invalid Account ID: 0.", exception.getMessage());
	}

	@Test
	public void purchaseTicketswithNoRequests() {
		InvalidPurchaseException exception = assertThrows(InvalidPurchaseException.class, 
				() -> ticketService.purchaseTickets(4L));
		assertEquals("Invalid Ticket Requests.", exception.getMessage());
	}

	@Test
	public void purchaseTicketswithNullRequests() {
		InvalidPurchaseException exception = assertThrows(InvalidPurchaseException.class, 
				() -> ticketService.purchaseTickets(5L, null));
		assertEquals("Invalid Ticket Requests.", exception.getMessage());
	}

	@Test
	public void purchaseTicketswithEmptyArray() {
		InvalidPurchaseException exception = assertThrows(InvalidPurchaseException.class, 
				() -> ticketService.purchaseTickets(6L, new TicketTypeRequest[] {}));
		assertEquals("Invalid Ticket Requests.", exception.getMessage());
	}

	@Test
	public void purchaseTicketsExceedsMaxLimit() {
		InvalidPurchaseException exception = assertThrows(InvalidPurchaseException.class, 
				() -> ticketService.purchaseTickets(6L, 
						new TicketTypeRequest(Type.ADULT, 20),
						new TicketTypeRequest(Type.CHILD, 6)));
		assertEquals("Maximum purchasable ticket limit 25 exceeded.", exception.getMessage());
	}

	@Test
	public void purchaseTicketsWithNoAdultTickets() {
		InvalidPurchaseException exception = assertThrows(InvalidPurchaseException.class, 
				() -> ticketService.purchaseTickets(6L, 
						new TicketTypeRequest(Type.INFANT, 1),
						new TicketTypeRequest(Type.CHILD, 5)));
		assertEquals("Child and Infant tickets cannot be purchased without purchasing an Adult ticket.",
				exception.getMessage());
	}

	@Test
	public void purchaseTicketsWithNoPriceDefined() {
		mockStatic(TicketPriceEnum.class);
		when(TicketPriceEnum.values()).thenReturn(new TicketPriceEnum[]{TicketPriceEnum.INFANT, TicketPriceEnum.ADULT});
		InvalidPurchaseException exception = assertThrows(InvalidPurchaseException.class, 
				() -> ticketService.purchaseTickets(6L, 
						new TicketTypeRequest(Type.INFANT, 1),
						new TicketTypeRequest(Type.CHILD, 2),
						new TicketTypeRequest(Type.ADULT, 5)));
		assertEquals("Price is not defined for ticket type: CHILD.",
				exception.getMessage());
	}
}
