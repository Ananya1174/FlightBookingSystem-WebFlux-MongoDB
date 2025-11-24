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


class DataInitializerTest {

    private ReactiveMongoTemplate mongoTemplate;
    private ReactiveIndexOperations idxOps;
    private DataInitializer dataInitializer;

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
    void run_createsIndexesAndInsertsSampleWhenEmpty() {
        ApplicationArguments args = mock(ApplicationArguments.class);

        dataInitializer.run(args);

        verify(idxOps, times(2)).createIndex(any());

        verify(mongoTemplate, times(1)).insert(any(AirlineInventory.class));
    }
}
