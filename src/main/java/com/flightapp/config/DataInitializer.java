package com.flightapp.config;

import org.springframework.boot.ApplicationArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.ReactiveIndexOperations;

import com.flightapp.model.AirlineInventory;

import reactor.core.publisher.Mono;
import java.time.LocalDateTime;

@Component
public class DataInitializer implements ApplicationRunner {

  private final ReactiveMongoTemplate mongoTemplate;
  private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

  public DataInitializer(ReactiveMongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public void run(ApplicationArguments args) {
    ReactiveIndexOperations invIdxOps = mongoTemplate.indexOps(AirlineInventory.class);

    Mono<Void> idxs = Mono.when(
        Mono.fromRunnable(() -> invIdxOps.createIndex(new Index().on("flightNumber", org.springframework.data.domain.Sort.Direction.ASC)).block()),
        Mono.fromRunnable(() -> invIdxOps.createIndex(new Index().on("origin", org.springframework.data.domain.Sort.Direction.ASC)
                .on("destination", org.springframework.data.domain.Sort.Direction.ASC)
                .on("departure", org.springframework.data.domain.Sort.Direction.ASC)).block())
    ).then();

    Mono<Long> countMono = mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(), AirlineInventory.class);

    idxs.then(countMono).flatMap(cnt -> {
      if (cnt == 0) {
        AirlineInventory sample = new AirlineInventory();
        sample.setAirline("Indigo");
        sample.setAirlineLogoUrl("");
        sample.setFlightNumber("IN123");
        sample.setOrigin("HYD");
        sample.setDestination("BLR");
        sample.setDeparture(LocalDateTime.now().plusDays(2).withHour(9).withMinute(0).withSecond(0).withNano(0));
        sample.setArrival(sample.getDeparture().plusHours(1).plusMinutes(30));
        sample.setTotalSeats(30);
        sample.setPrice(4500.0);
        sample.setAvailableSeats(java.util.stream.IntStream.rangeClosed(1, 30).mapToObj(i -> "S" + i).toList());
        return mongoTemplate.insert(sample).then();
      }
      return Mono.empty();
    }).subscribe(
      v -> {},
      err -> log.error("DataInitializer error: {}", err.getMessage())    );
  }
}
