package io.smallrye.mutiny.operators;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.Test;

import io.smallrye.mutiny.CompositeException;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.UniEmitter;
import io.smallrye.mutiny.tuples.Functions;

public class UniOnItemOrFailureFlatMapTest {

    private Uni<Integer> one = Uni.createFrom().item(1);
    private Uni<Integer> async_one = one.onItem().delayIt().by(Duration.ofMillis(10));
    private Uni<Void> none = Uni.createFrom().nullItem();
    private Uni<Integer> failed = Uni.createFrom().failure(new IOException("boom"));
    private Uni<Integer> async_failed = Uni.createFrom()
            .emitter(e -> new Thread(() -> e.fail(new IOException("boom"))).start());

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testThatMapperIsNotNull() {
        one.onItemOrFailure().produceUni(
                (Functions.TriConsumer<? super Integer, Throwable, UniEmitter<? super Object>>) null);
    }

    @Test
    public void testWithImmediateItem() {
        UniAssertSubscriber<Integer> test = UniAssertSubscriber.create();
        AtomicInteger count = new AtomicInteger();
        one.onItemOrFailure().produceUni((v, f) -> {
            assertThat(f).isNull();
            count.incrementAndGet();
            return Uni.createFrom().item(2);
        }).subscribe().withSubscriber(test);

        test.assertCompletedSuccessfully().assertItem(2).assertNoFailure();
        assertThat(count).hasValue(1);
    }

    @Test
    public void testWithDelayedItem() {
        UniAssertSubscriber<Integer> test = UniAssertSubscriber.create();
        AtomicInteger count = new AtomicInteger();
        async_one.onItemOrFailure().produceUni((v, f) -> {
            assertThat(f).isNull();
            count.incrementAndGet();
            return Uni.createFrom().item(2);
        }).subscribe().withSubscriber(test);

        test.await().assertCompletedSuccessfully().assertItem(2).assertNoFailure();
        assertThat(count).hasValue(1);
    }

    @Test
    public void testWithImmediateNullItem() {
        UniAssertSubscriber<Integer> test = UniAssertSubscriber.create();
        AtomicInteger count = new AtomicInteger();
        none.onItemOrFailure().produceUni((v, f) -> {
            assertThat(f).isNull();
            count.incrementAndGet();
            return Uni.createFrom().item(2);
        }).subscribe().withSubscriber(test);

        test.assertCompletedSuccessfully().assertItem(2).assertNoFailure();
        assertThat(count).hasValue(1);
    }

    @Test
    public void testWithImmediateFailure() {
        UniAssertSubscriber<Integer> test = UniAssertSubscriber.create();
        AtomicInteger count = new AtomicInteger();
        failed.onItemOrFailure().produceUni((v, f) -> {
            assertThat(f).isNotNull().isInstanceOf(IOException.class).hasMessageContaining("boom");
            count.incrementAndGet();
            return Uni.createFrom().item(2);
        }).subscribe().withSubscriber(test);

        test.assertCompletedSuccessfully().assertItem(2).assertNoFailure();
        assertThat(count).hasValue(1);
    }

    @Test
    public void testWithDelayedFailure() {
        UniAssertSubscriber<Integer> test = UniAssertSubscriber.create();
        AtomicInteger count = new AtomicInteger();
        async_failed.onItemOrFailure().produceUni((v, f) -> {
            assertThat(f).isNotNull().isInstanceOf(IOException.class).hasMessageContaining("boom");
            count.incrementAndGet();
            return Uni.createFrom().item(2);
        }).subscribe().withSubscriber(test);

        test.await().assertCompletedSuccessfully().assertItem(2).assertNoFailure();
        assertThat(count).hasValue(1);
    }

    @Test
    public void testWithImmediateCancellation() {
        UniAssertSubscriber<Integer> test = new UniAssertSubscriber<>(true);
        AtomicInteger count = new AtomicInteger();
        one.onItemOrFailure().produceUni((v, f) -> {
            count.incrementAndGet();
            return Uni.createFrom().item(2);
        }).subscribe().withSubscriber(test);
        test.assertNotCompleted();
        assertThat(count).hasValue(0);
    }

    @Test
    public void testProducingDeferredUni() {
        UniAssertSubscriber<Integer> test1 = UniAssertSubscriber.create();
        UniAssertSubscriber<Integer> test2 = UniAssertSubscriber.create();
        AtomicInteger count = new AtomicInteger(2);
        Uni<Integer> uni = one.onItemOrFailure()
                .produceUni((v, f) -> Uni.createFrom().deferred(() -> Uni.createFrom().item(count.incrementAndGet())));
        uni.subscribe().withSubscriber(test1);
        uni.subscribe().withSubscriber(test2);
        test1.assertCompletedSuccessfully().assertItem(3).assertNoFailure();
        test2.assertCompletedSuccessfully().assertItem(4).assertNoFailure();
    }

    @Test
    public void testWithImmediateItemAndThrowingException() {
        UniAssertSubscriber<Integer> test = UniAssertSubscriber.create();
        AtomicInteger count = new AtomicInteger();
        one.onItemOrFailure().<Integer> produceUni((v, f) -> {
            assertThat(f).isNull();
            count.incrementAndGet();
            throw new IllegalStateException("kaboom");
        }).subscribe().withSubscriber(test);

        test.await().assertFailure(IllegalStateException.class, "kaboom");
        assertThat(count).hasValue(1);
    }

    @Test
    public void testWithDeferredItemAndThrowingException() {
        UniAssertSubscriber<Integer> test = UniAssertSubscriber.create();
        AtomicInteger count = new AtomicInteger();
        async_one.onItemOrFailure().<Integer> produceUni((v, f) -> {
            assertThat(f).isNull();
            count.incrementAndGet();
            throw new IllegalStateException("kaboom");
        }).subscribe().withSubscriber(test);

        test.await().assertFailure(IllegalStateException.class, "kaboom");
        assertThat(count).hasValue(1);
    }

    @Test
    public void testWithImmediateFailureAndThrowingException() {
        UniAssertSubscriber<Integer> test = UniAssertSubscriber.create();
        AtomicInteger count = new AtomicInteger();
        failed.onItemOrFailure().<Integer> produceUni((v, f) -> {
            assertThat(v).isNull();
            assertThat(f).isNotNull();
            count.incrementAndGet();
            throw new IllegalStateException("kaboom");
        }).subscribe().withSubscriber(test);

        test.await().assertFailure(CompositeException.class, "kaboom");
        test.await().assertFailure(CompositeException.class, "boom");
        assertThat(count).hasValue(1);
    }

    @Test
    public void testWithAMapperReturningNull() {
        UniAssertSubscriber<Integer> test = UniAssertSubscriber.create();
        AtomicBoolean called = new AtomicBoolean();
        one
                .onItemOrFailure().<Integer> produceUni((v, f) -> {
                    called.set(true);
                    return null;
                }).subscribe().withSubscriber(test);
        test.await().assertCompletedWithFailure().assertFailure(NullPointerException.class, "");
        assertThat(called).isTrue();
    }

    @Test
    public void testWithAMapperReturningNullAfterFailure() {
        UniAssertSubscriber<Integer> test = UniAssertSubscriber.create();
        AtomicBoolean called = new AtomicBoolean();
        failed
                .onItemOrFailure().<Integer> produceUni((v, f) -> {
                    assertThat(f).isNotNull();
                    called.set(true);
                    return null;
                }).subscribe().withSubscriber(test);
        test.await().assertCompletedWithFailure().assertFailure(NullPointerException.class, "");
        assertThat(called).isTrue();
    }

    @Test
    public void testWithCancellationBeforeEmission() {
        UniAssertSubscriber<Integer> test = UniAssertSubscriber.create();
        AtomicBoolean cancelled = new AtomicBoolean();
        CompletableFuture<Integer> future = new CompletableFuture<Integer>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                cancelled.set(true);
                return true;
            }
        };

        Uni<Integer> uni = Uni.createFrom().item(1).onItemOrFailure()
                .produceUni((v, f) -> Uni.createFrom().completionStage(future));
        uni.subscribe().withSubscriber(test);
        test.cancel();
        test.assertNotCompleted();
        assertThat(cancelled).isTrue();
    }

    @Test
    public void testWithEmitterOnItem() {
        UniAssertSubscriber<Integer> test = UniAssertSubscriber.create();
        one.onItemOrFailure().<Integer> produceUni((i, f, e) -> {
            assertThat(i).isEqualTo(1);
            assertThat(f).isNull();
            e.complete(2);
        }).subscribe().withSubscriber(test);

        test.await().assertItem(2).assertCompletedSuccessfully();
    }

    @Test
    public void testWithEmitterOnNull() {
        UniAssertSubscriber<Integer> test = UniAssertSubscriber.create();
        none.onItemOrFailure().<Integer> produceUni((i, f, e) -> {
            assertThat(i).isNull();
            assertThat(f).isNull();
            e.complete(2);
        }).subscribe().withSubscriber(test);

        test.await().assertItem(2).assertCompletedSuccessfully();
    }

    @Test
    public void testWithEmitterOnFailure() {
        UniAssertSubscriber<Integer> test = UniAssertSubscriber.create();
        failed.onItemOrFailure().<Integer> produceUni((i, f, e) -> {
            assertThat(i).isNull();
            assertThat(f).isNotNull().isInstanceOf(IOException.class);
            e.complete(2);
        }).subscribe().withSubscriber(test);

        test.await().assertItem(2).assertCompletedSuccessfully();
    }

    @Test
    public void testWithEmitterOnItemThrowingException() {
        UniAssertSubscriber<Integer> test = UniAssertSubscriber.create();
        one.onItemOrFailure().<Integer> produceUni((i, f, e) -> {
            assertThat(i).isEqualTo(1);
            assertThat(f).isNull();
            throw new IllegalStateException("bing");
        }).subscribe().withSubscriber(test);

        test.await().assertFailure(IllegalStateException.class, "bing");
    }

    @Test
    public void testWithEmitterOnFailureThrowingException() {
        UniAssertSubscriber<Integer> test = UniAssertSubscriber.create();
        failed.onItemOrFailure().<Integer> produceUni((i, f, e) -> {
            throw new IllegalStateException("bing");
        }).subscribe().withSubscriber(test);

        test.await().assertFailure(CompositeException.class, "bing");
        test.await().assertFailure(CompositeException.class, "boom");
    }

}
