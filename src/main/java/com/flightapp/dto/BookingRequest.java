package com.flightapp.dto;

import java.util.List;
import com.flightapp.model.Passenger;
import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;

@Data
public class BookingRequest {
  @NotEmpty private String name;
  @Email @NotEmpty private String email;
  @NotEmpty private List<Passenger> passengers;
  @NotEmpty private List<String> seatNumbers;
  private boolean mealVeg;
}