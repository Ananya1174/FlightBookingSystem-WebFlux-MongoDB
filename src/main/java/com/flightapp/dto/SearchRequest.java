package com.flightapp.dto;

import jakarta.validation.constraints.NotEmpty;

public class SearchRequest {
  
  @NotEmpty private String origin;
  @NotEmpty private String destination;
  @NotEmpty private String from; // ISO date-time
  @NotEmpty private String to;
  public String getOrigin() {
	return origin;
  }
  public void setOrigin(String origin) {
	this.origin = origin;
  }
  public String getDestination() {
	return destination;
  }
  public void setDestination(String destination) {
	this.destination = destination;
  }
  public String getFrom() {
	return from;
  }
  public void setFrom(String from) {
	this.from = from;
  }
  public String getTo() {
	return to;
  }
  public void setTo(String to) {
	this.to = to;
  }

  }