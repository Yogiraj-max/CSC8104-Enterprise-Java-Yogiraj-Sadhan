package uk.ac.newcastle.enterprisemiddleware.travelagent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import uk.ac.newcastle.enterprisemiddleware.booking.Booking;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * An aggregate booking made by the TravelAgent: a flight (this service), a taxi and a hotel (colleagues' services),
 * all for the same customer on the same date.
 *
 * As the coursework suggests, the local flight Booking is linked with a JPA relationship (@OneToOne), while the
 * upstream taxi and hotel bookings are stored only by their remote ids.
 */
@Entity
@NamedQueries({
        @NamedQuery(name = TravelAgentBooking.FIND_ALL, query = "SELECT t FROM TravelAgentBooking t ORDER BY t.id ASC"),
        @NamedQuery(name = TravelAgentBooking.FIND_BY_CUSTOMER,
                query = "SELECT t FROM TravelAgentBooking t WHERE t.flightBooking.customer.id = :customerId ORDER BY t.id ASC")
})
@Table(name = "travel_agent_booking")
@Schema(description = "A flight, taxi and hotel booked together for one customer on one date")
public class TravelAgentBooking implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String FIND_ALL = "TravelAgentBooking.findAll";
    public static final String FIND_BY_CUSTOMER = "TravelAgentBooking.findByCustomer";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The local flight booking. Deleting it (directly or by cascade) also deletes this record. */
    @JsonIgnore
    @NotNull
    @OneToOne(optional = false)
    @JoinColumn(name = "flight_booking_id", nullable = false, unique = true)
    private Booking flightBooking;

    @NotNull
    @Column(name = "taxi_booking_id", nullable = false)
    @Schema(description = "Id of the booking in the Taxi service")
    private Long taxiBookingId;

    @NotNull
    @Column(name = "hotel_booking_id", nullable = false)
    @Schema(description = "Id of the booking in the Hotel service")
    private Long hotelBookingId;

    @NotNull
    @Column(name = "taxi_id", nullable = false)
    @Schema(description = "Id of the taxi in the Taxi service")
    private Long taxiId;

    @NotNull
    @Column(name = "hotel_id", nullable = false)
    @Schema(description = "Id of the hotel in the Hotel service")
    private Long hotelId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Booking getFlightBooking() { return flightBooking; }
    public void setFlightBooking(Booking flightBooking) { this.flightBooking = flightBooking; }

    public Long getTaxiBookingId() { return taxiBookingId; }
    public void setTaxiBookingId(Long taxiBookingId) { this.taxiBookingId = taxiBookingId; }

    public Long getHotelBookingId() { return hotelBookingId; }
    public void setHotelBookingId(Long hotelBookingId) { this.hotelBookingId = hotelBookingId; }

    public Long getTaxiId() { return taxiId; }
    public void setTaxiId(Long taxiId) { this.taxiId = taxiId; }

    public Long getHotelId() { return hotelId; }
    public void setHotelId(Long hotelId) { this.hotelId = hotelId; }

    // ---- Read-only values shown in the JSON, taken from the local flight booking ----

    @Schema(description = "Id of the local flight Booking")
    public Long getFlightBookingId() { return flightBooking != null ? flightBooking.getId() : null; }

    @Schema(description = "Id of the Customer (in this service) the booking is for")
    public Long getCustomerId() { return flightBooking != null ? flightBooking.getCustomerId() : null; }

    @Schema(description = "Id of the Flight (in this service)")
    public Long getFlightId() { return flightBooking != null ? flightBooking.getFlightId() : null; }

    @Schema(description = "Date of travel")
    public LocalDate getBookingDate() { return flightBooking != null ? flightBooking.getBookingDate() : null; }

    @Override
    public String toString() {
        return "TravelAgentBooking{id=" + id + ", flightBookingId=" + getFlightBookingId() + ", taxiBookingId=" + taxiBookingId
                + ", hotelBookingId=" + hotelBookingId + ", date=" + getBookingDate() + "}";
    }
}
