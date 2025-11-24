package com.flightapp.util;

import java.security.SecureRandom;

public class PnrGenerator {
  private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  private static final SecureRandom RANDOM = new SecureRandom();
  private PnrGenerator() {
      // prevent instantiation
  }
  public static String generate() {
    StringBuilder sb = new StringBuilder(6);
    for (int i = 0; i < 6; i++) {
      sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    }
    return sb.toString();
  }
}