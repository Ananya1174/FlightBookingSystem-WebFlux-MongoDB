package com.flightapp.dto;

import java.util.List;
import com.flightapp.model.Passenger;
import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;

@Data
public class BookingRequest {
  @NotEmpty(message = "Name required") private String name;
  @Email(message = "Email must be valid") @NotEmpty(message = "Email required") private String email;
  @NotEmpty(message = "At least one passenger") private List<Passenger> passengers;
  @NotEmpty(message = "Select at least one seat") private List<String> seatNumbers;
  private boolean mealVeg;
}