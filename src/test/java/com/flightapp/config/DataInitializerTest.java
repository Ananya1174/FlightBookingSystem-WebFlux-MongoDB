package com.flightapp.config;

import com.flightapp.model.AirlineInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.ReactiveIndexOperations;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DataInitializer.
 *
 * The initializer creates two indexes and inserts a sample AirlineInventory when the collection is empty.
 */
class DataInitializerTest {

    private ReactiveMongoTemplate mongoTemplate;
    private ReactiveIndexOperations idxOps;
    private DataInitializer dataInitializer;

    @BeforeEach
    void setup() {
        mongoTemplate = mock(ReactiveMongoTemplate.class);
        idxOps = mock(ReactiveIndexOperations.class);

        // indexOps(...) returns the mocked index operations
        when(mongoTemplate.indexOps(AirlineInventory.class)).thenReturn(idxOps);

        // createIndex(...) returns a Mono<String>
        when(idxOps.createIndex(any())).thenReturn(Mono.just("idx"));

        // simulate empty collection so initializer inserts sample
        when(mongoTemplate.count(any(), eq(AirlineInventory.class))).thenReturn(Mono.just(0L));
        when(mongoTemplate.insert(any(AirlineInventory.class))).thenReturn(Mono.just(new AirlineInventory()));

        dataInitializer = new DataInitializer(mongoTemplate);
    }

    @Test
    void run_createsIndexesAndInsertsSampleWhenEmpty() {
        // run with a mocked ApplicationArguments (not used by the initializer, but non-null is nicer)
        ApplicationArguments args = mock(ApplicationArguments.class);

        dataInitializer.run(args);

        // DataInitializer creates two indexes: flightNumber, and origin+destination+departure
        verify(idxOps, times(2)).createIndex(any());

        // Since count returned 0, it should attempt to insert a sample inventory
        verify(mongoTemplate, times(1)).insert(any(AirlineInventory.class));
    }
}