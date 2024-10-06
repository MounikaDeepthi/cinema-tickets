package uk.gov.dwp.uc.pairtest;

import java.lang.System.Logger.Level;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;
import uk.gov.dwp.uc.pairtest.enums.ErrorMessages;
import uk.gov.dwp.uc.pairtest.enums.TicketPriceEnum;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

public class TicketServiceImpl implements TicketService {

	private static final System.Logger logger = System.getLogger(TicketServiceImpl.class.getName());
	private static final int ticketsMaxLimit = 25;
	private SeatReservationService seatReservationService;
	private TicketPaymentService ticketPaymentService;

	public TicketServiceImpl(SeatReservationService seatReservationService, TicketPaymentService ticketPaymentService) {
		this.seatReservationService = seatReservationService;
		this.ticketPaymentService = ticketPaymentService;
	}

	/**
	 * Should only have private methods other than the one below.
	 */
	@Override
	public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests) throws InvalidPurchaseException {
		String requestId = UUID.randomUUID().toString();
		logger.log(Level.INFO, "Request ID: {0}, Ticket Purchase Request Received, Id: {1}", requestId, accountId);

		if(!isAccountIdValid(accountId)) {
			logger.log(Level.ERROR, "Request ID: {0}, Invalid Account ID: {1}", requestId, accountId);
			throw new InvalidPurchaseException(String.format(ErrorMessages.INVALID_ACCOUNT.getMessage(), accountId));
		}

		if(Objects.isNull(ticketTypeRequests) || ticketTypeRequests.length == 0) {
			logger.log(Level.ERROR, "Request ID: {0}, Null or Empty Request", requestId);
			throw new InvalidPurchaseException(ErrorMessages.EMPTY_REQUEST.getMessage());
		}

		logger.log(Level.INFO, "Request ID: {0}, Ticket Purchase Request Details, Id: {1} & No of Requests: {2}", 
				requestId, accountId, ticketTypeRequests.length);
		Map<Type, Integer> totalNoOfTicketsByType = groupTotalNoOfTicketsByType(ticketTypeRequests);
		int totalNoOfTickets = getTotalNoOfTickets(totalNoOfTicketsByType);

		if(ticketMaxLimitExceeded(totalNoOfTickets)) {
			logger.log(Level.ERROR, "Request ID: {0}, Ticket Max limit exceeded, Total: {1} & Limit: {2}", 
					requestId, totalNoOfTickets, ticketsMaxLimit);
			throw new InvalidPurchaseException(String.format(
					ErrorMessages.MAX_LIMIT_EXCEDED.getMessage(), ticketsMaxLimit));
		}

		if(!adultAccompanied(totalNoOfTicketsByType)) {
			logger.log(Level.ERROR, "Request ID: {0}, no adult tickets are found in the request.", requestId);
			throw new InvalidPurchaseException(ErrorMessages.NO_ADULT_ACCOMPANY.getMessage());
		}

		makePayment(requestId, accountId, totalNoOfTicketsByType);
		reserveSeat(requestId, accountId, totalNoOfTickets, totalNoOfTicketsByType);
		logger.log(Level.INFO, "Request ID: {0}, Ticket Purchase processed succcessfully.", requestId);
	}

	private boolean isAccountIdValid(Long accountId) {
		return (accountId > 0);
	}

	private boolean ticketMaxLimitExceeded(int totalNoOfTickets) {
		return (totalNoOfTickets > ticketsMaxLimit);
	}

	private boolean adultAccompanied(Map<Type, Integer> totalNoOfTicketsByType) {
		int adultTickets = totalNoOfTicketsByType.getOrDefault(Type.ADULT, 0);
		return (adultTickets != 0);
	}

	private int getTotalNoOfTickets(Map<Type, Integer> totalNoOfTicketsByType) {
		return totalNoOfTicketsByType.values().stream().mapToInt(Integer::intValue).sum();
	}

	private Map<Type, Integer> groupTotalNoOfTicketsByType(TicketTypeRequest... ticketTypeRequests) {
		return Arrays.stream(ticketTypeRequests)
				.collect(Collectors.groupingBy(TicketTypeRequest::getTicketType, 
						Collectors.summingInt(TicketTypeRequest::getNoOfTickets)));
	}

	private void makePayment(String requestId, Long accountId, Map<Type, Integer> totalNoOfTicketsByType) {
		BigDecimal totalAmountToPay = calculateTotalAmountToPay(requestId, totalNoOfTicketsByType);
		logger.log(Level.INFO, "Request ID: {0}, Payment Request Details: Id: {1} & Amount: {2}", 
				requestId, accountId, totalAmountToPay);
		ticketPaymentService.makePayment(accountId, totalAmountToPay.intValue());
	}

	private void reserveSeat(String requestId, Long accountId, int totalNoOfTickets, Map<Type, Integer> totalNoOfTicketsByType) {
		int infantTickets = totalNoOfTicketsByType.getOrDefault(Type.INFANT, 0).intValue();
		int seatsToReserve = totalNoOfTickets-infantTickets;
		logger.log(Level.INFO, "Request ID: {0}, Seat Reservation Request "
				+ "Details: Id: {1}, Total Tickets: {2}, Infant Tickets: {3} & SeatsToReserve: {4}", 
				requestId, accountId, totalNoOfTickets, infantTickets, seatsToReserve);
		seatReservationService.reserveSeat(accountId, seatsToReserve);
	}

	private BigDecimal calculateTotalAmountToPay(String requestId, Map<Type, Integer> totalNoOfTicketsByType) {
		BigDecimal totalAmountToPay = totalNoOfTicketsByType.entrySet().stream()
				.map(t -> {
					logger.log(Level.INFO, "Request ID: {0}, Ticket Type: {1} & NoOfTickets: {2}", 
							requestId, t.getKey(), t.getValue());
					TicketPriceEnum ticketTypePriceEnum = getTicketPriceEnum(requestId, t.getKey());

					return ticketTypePriceEnum.getPrice().multiply(BigDecimal.valueOf(t.getValue()));
				}).reduce(BigDecimal.ZERO, BigDecimal::add);
		return totalAmountToPay;
	}

	private TicketPriceEnum getTicketPriceEnum(String requestId, Type ticketType) {
		Optional<TicketPriceEnum> optionalTicketPriceEnum = Arrays.stream(TicketPriceEnum.values())
				.filter(t -> ticketType.name().equalsIgnoreCase(t.name()))
				.findAny();
		if(!optionalTicketPriceEnum.isPresent()) {
			logger.log(Level.ERROR, "Request ID: {0}, Price not found for ticket type: {1}", 
					requestId, ticketType.name());
			throw new InvalidPurchaseException(String.format(
					ErrorMessages.PRICE_UNDEFINED.getMessage(), ticketType.name()));
		}
		return optionalTicketPriceEnum.get();
	}
}

