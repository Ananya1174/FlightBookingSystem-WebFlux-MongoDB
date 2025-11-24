package com.flightapp.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class Passenger {
  @NotBlank(message = "Passenger name required")
  private String name;

  @NotBlank(message = "Gender required")
  @Pattern(regexp = "^(M|F|Other)$", message = "Gender must be M, F or Other")
  private String gender;

  @NotNull(message = "Age required")
  @Min(value = 0, message = "Age must be >= 0")
  private Integer age;
}
