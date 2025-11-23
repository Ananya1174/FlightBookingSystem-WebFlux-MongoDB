package com.flightapp.model;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;

@Data
@Document(collection = "inventories")
public class AirlineInventory {
  @Id
  private String id;            
  private String airline;      
  private String airlineLogoUrl;
  private String flightNumber;
  private String origin;
  private String destination;
  private LocalDateTime departure;
  private LocalDateTime arrival;
  private int totalSeats;
  private double price;
  private List<String> availableSeats; 
}