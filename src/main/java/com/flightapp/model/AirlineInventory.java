package com.flightapp.model;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Future;
import lombok.Data;

@Data
@Document(collection = "inventories")
public class AirlineInventory {
  @Id
  private String id;            

  @NotBlank(message = "Airline name is required")
  private String airline;      

    private String airlineLogoUrl;

  @NotBlank(message = "Flight number is required")
  private String flightNumber;

  @NotBlank(message = "Origin is required")
  private String origin;

  @NotBlank(message = "Destination is required")
  private String destination;

  @NotNull(message = "Departure date/time required")
  @Future(message = "Departure must be in the future")
  private LocalDateTime departure;

  @NotNull(message = "Arrival date/time required")
  private LocalDateTime arrival;

  @Positive(message = "Total seats must be positive")
  private int totalSeats;

  @Positive(message = "Price must be positive")
  private double price;

  private List<String> availableSeats; 
}