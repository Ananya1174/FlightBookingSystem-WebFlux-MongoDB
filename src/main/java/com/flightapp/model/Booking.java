package com.flightapp.model;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;


@Data
@Document(collection = "bookings")
public class Booking {
@Id
private String id;
private String pnr;
private String flightId;
private String email;
private String name;
private List<Passenger> passengers;
private List<String> seatNumbers;
private boolean mealVeg;
private LocalDateTime bookedAt;
private boolean canceled = false;
private LocalDateTime canceledAt;   
private LocalDateTime journeyDate;
}