package uk.ac.newcastle.enterprisemiddleware.travelagent;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Request body for creating a TravelAgentBooking. Not persisted.
 *
 * customerId and flightId are ids in THIS service; taxiId and hotelId are ids in the colleagues' services
 * (see GET /travelagent/taxis and GET /travelagent/hotels).
 */
@Schema(description = "What to book: a local customer and flight, a remote taxi and hotel, and the date")
public class TravelAgentBookingRequest {

    @Schema(example = "1", description = "Id of a Customer in this service")
    private Long customerId;

    @Schema(example = "1", description = "Id of a Flight in this service")
    private Long flightId;

    @Schema(example = "1", description = "Id of a Taxi in the Taxi service (GET /travelagent/taxis)")
    private Long taxiId;

    @Schema(example = "1", description = "Id of a Hotel in the Hotel service (GET /travelagent/hotels)")
    private Long hotelId;

    @Schema(example = "2027-01-15", description = "Date of travel, yyyy-MM-dd, must be in the future")
    private LocalDate bookingDate;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public Long getFlightId() { return flightId; }
    public void setFlightId(Long flightId) { this.flightId = flightId; }

    public Long getTaxiId() { return taxiId; }
    public void setTaxiId(Long taxiId) { this.taxiId = taxiId; }

    public Long getHotelId() { return hotelId; }
    public void setHotelId(Long hotelId) { this.hotelId = hotelId; }

    public LocalDate getBookingDate() { return bookingDate; }
    public void setBookingDate(LocalDate bookingDate) { this.bookingDate = bookingDate; }
}
