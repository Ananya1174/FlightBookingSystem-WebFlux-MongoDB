package com.flightapp.config;

import com.flightapp.model.AirlineInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.ReactiveIndexOperations;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.*;

class DataInitializerTest {

    ReactiveMongoTemplate mongoTemplate;
    DataInitializer dataInitializer;
    ReactiveIndexOperations idxOps;

    @BeforeEach
    void setup() {
        mongoTemplate = mock(ReactiveMongoTemplate.class);
        idxOps = mock(ReactiveIndexOperations.class);
        when(mongoTemplate.indexOps(AirlineInventory.class)).thenReturn(idxOps);
        when(idxOps.createIndex(any())).thenReturn(Mono.just("idx"));
        when(mongoTemplate.count(any(), eq(AirlineInventory.class))).thenReturn(Mono.just(0L));
        when(mongoTemplate.insert(any(AirlineInventory.class))).thenReturn(Mono.just(new AirlineInventory()));
        dataInitializer = new DataInitializer(mongoTemplate);
    }

    @Test
    void run_createsIndexAndInsertsSample() {
        dataInitializer.run(null);
        // subscribe happens inside run() so we can just verify invocations
        verify(idxOps).createIndex(any());
        verify(mongoTemplate).insert(any(AirlineInventory.class));
    }
}