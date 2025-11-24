package com.flightapp.dto;

import java.util.List;

import com.flightapp.model.Passenger;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class BookingUpdateRequest {
  @Email(message = "Email must be valid") @NotEmpty(message = "Email required") private String email;
  private String name;

  private List<Passenger> passengers;

  private List<String> seatNumbers;

  private Boolean mealVeg;
}
