package com.danno.invoice_generator;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
public abstract class PostgresIntegrationTest {

    // Not annotated with @Container: that ties JUnit's lifecycle to each subclass's
    // afterAll and stops the shared container as soon as the first IT class finishes,
    // leaving it dead for the next one. Started once, manually, for the whole JVM.
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        postgres.start();
    }
}
