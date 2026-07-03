package com.danno.invoice_generator.infrastructure;

import com.danno.invoice_generator.PostgresIntegrationTest;
import com.danno.invoice_generator.application.port.InvoiceNumberGenerator;
import com.danno.invoice_generator.domain.InvoiceNumber;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JpaInvoiceNumberGeneratorIT extends PostgresIntegrationTest {

    @Autowired
    private InvoiceNumberGenerator generator;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void given_newFiscalYear_when_nextNumber_then_returnsSequenceOne() {
        InvoiceNumber number = inNewTransaction(() -> generator.nextNumber(5000));

        assertThat(number.value()).isEqualTo("INV-5000-000001");
    }

    @Test
    void given_existingSequenceRow_when_nextNumberCalledTwice_then_incrementsSequentially() {
        InvoiceNumber first = inNewTransaction(() -> generator.nextNumber(5001));
        InvoiceNumber second = inNewTransaction(() -> generator.nextNumber(5001));

        assertThat(first.value()).isEqualTo("INV-5001-000001");
        assertThat(second.value()).isEqualTo("INV-5001-000002");
    }

    @Test
    void given_concurrentCallsForSameFiscalYear_when_nextNumberInvokedFromMultipleThreads_then_allNumbersAreUniqueAndGapless()
            throws Exception {
        int fiscalYear = 5002;
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Callable<InvoiceNumber>> tasks = IntStream.range(0, threadCount)
                    .<Callable<InvoiceNumber>>mapToObj(i -> () -> inNewTransaction(() -> generator.nextNumber(fiscalYear)))
                    .collect(Collectors.toList());

            List<Future<InvoiceNumber>> futures = executor.invokeAll(tasks);

            Set<Long> sequences = futures.stream()
                    .map(this::getUnchecked)
                    .map(this::sequenceOf)
                    .collect(Collectors.toSet());

            assertThat(sequences).containsExactlyInAnyOrderElementsOf(
                    LongStream.rangeClosed(1, threadCount).boxed().collect(Collectors.toList()));
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void given_differentFiscalYears_when_nextNumberCalledConcurrently_then_eachYearSequenceIsIndependent() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<InvoiceNumber> yearA = executor.submit(() -> inNewTransaction(() -> generator.nextNumber(5003)));
            Future<InvoiceNumber> yearB = executor.submit(() -> inNewTransaction(() -> generator.nextNumber(5004)));

            assertThat(yearA.get().value()).isEqualTo("INV-5003-000001");
            assertThat(yearB.get().value()).isEqualTo("INV-5004-000001");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void given_generatorCalledOutsideAnActiveTransaction_when_nextNumber_then_throwsIllegalTransactionStateException() {
        assertThatThrownBy(() -> generator.nextNumber(5005))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private InvoiceNumber inNewTransaction(Supplier<InvoiceNumber> action) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> action.get());
    }

    private InvoiceNumber getUnchecked(Future<InvoiceNumber> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private long sequenceOf(InvoiceNumber number) {
        String value = number.value();
        return Long.parseLong(value.substring(value.lastIndexOf('-') + 1));
    }
}
