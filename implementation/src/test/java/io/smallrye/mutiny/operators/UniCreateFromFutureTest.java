package io.smallrye.mutiny.operators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;

@SuppressWarnings("ConstantConditions")
public class UniCreateFromFutureTest {

    @Test
    public void testThatNullValueAreAccepted() {
        UniAssertSubscriber<String> subscriber = UniAssertSubscriber.create();
        CompletableFuture<String> cs = new CompletableFuture<>();
        Uni.createFrom().future(cs).subscribe().withSubscriber(subscriber);
        cs.complete(null);
        subscriber
                .await()
                .assertCompleted().assertItem(null);
    }

    @Test
    public void testWithAnAlreadySuccessfullyCompletedFuture() {
        UniAssertSubscriber<String> subscriber = UniAssertSubscriber.create();
        CompletableFuture<String> cs = new CompletableFuture<>();
        cs.complete("1");
        Uni.createFrom().future(cs).subscribe().withSubscriber(subscriber);
        // No await - immediate emission
        subscriber
                .assertCompleted().assertItem("1");
    }

    @Test
    public void testWithAnAlreadyCancelledFuture() {
        UniAssertSubscriber<String> subscriber = UniAssertSubscriber.create();
        CompletableFuture<String> cs = new CompletableFuture<>();
        cs.cancel(false);
        Uni.createFrom().future(cs).subscribe().withSubscriber(subscriber);
        // No await - immediate failure
        subscriber
                .assertFailedWith(CancellationException.class, null);
    }

    @Test
    public void testWithNonNullValue() {
        UniAssertSubscriber<String> subscriber = UniAssertSubscriber.create();
        CompletableFuture<String> cs = new CompletableFuture<>();
        Uni.createFrom().future(cs).subscribe().withSubscriber(subscriber);
        cs.complete("1");
        subscriber
                .await()
                .assertCompleted().assertItem("1");
    }

    @Test
    public void testWithException() {
        UniAssertSubscriber<String> subscriber = UniAssertSubscriber.create();
        CompletableFuture<String> cs = new CompletableFuture<>();
        Uni.createFrom().future(cs).subscribe().withSubscriber(subscriber);
        cs.completeExceptionally(new IOException("boom"));
        subscriber
                .await()
                .assertFailedWith(IOException.class, "boom");
    }

    @Test
    public void testWithAFutureThatHasAlreadyFailed() {
        UniAssertSubscriber<String> subscriber = UniAssertSubscriber.create();
        CompletableFuture<String> cs = new CompletableFuture<>();
        cs.completeExceptionally(new IOException("boom"));
        Uni.createFrom().future(cs).subscribe().withSubscriber(subscriber);
        // No await - immediate failure
        subscriber
                .assertFailedWith(IOException.class, "boom");
    }

    @Test
    public void testWithExceptionThrownByAStage() {
        UniAssertSubscriber<String> subscriber = UniAssertSubscriber.create();
        CompletableFuture<String> cs = new CompletableFuture<>();
        Uni.createFrom().future(() -> cs
                .thenApply(String::toUpperCase)
                .<String> thenApply(s -> {
                    throw new IllegalStateException("boom");
                })).subscribe().withSubscriber(subscriber);
        cs.complete("bonjour");
        subscriber
                .await()
                .assertFailedWith(IllegalStateException.class, "boom");
    }

    @Test
    public void testThatNullValueAreAcceptedWithSupplier() {
        UniAssertSubscriber<Void> subscriber = UniAssertSubscriber.create();
        Uni.createFrom().<Void> future(() -> CompletableFuture.completedFuture(null)).subscribe()
                .withSubscriber(subscriber);
        subscriber.assertCompleted().assertItem(null);
    }

    @Test
    public void testWithNonNullValueWithSupplier() {
        UniAssertSubscriber<String> subscriber = UniAssertSubscriber.create();
        CompletableFuture<String> cs = new CompletableFuture<>();
        Uni.createFrom().future(() -> cs).subscribe().withSubscriber(subscriber);
        cs.complete("1");
        subscriber
                .await()
                .assertCompleted().assertItem("1");
    }

    @Test
    public void testWithExceptionWithSupplier() {
        UniAssertSubscriber<String> subscriber = UniAssertSubscriber.create();
        CompletableFuture<String> cs = new CompletableFuture<>();
        Uni.createFrom().future(() -> cs).subscribe().withSubscriber(subscriber);
        cs.completeExceptionally(new IOException("boom"));
        subscriber
                .await()
                .assertFailedWith(IOException.class, "boom");
    }

    @Test
    public void testWithExceptionInSupplier() {
        UniAssertSubscriber<String> subscriber = UniAssertSubscriber.create();
        Uni.createFrom().<String> future(() -> {
            throw new NullPointerException("boom");
        }).subscribe().withSubscriber(subscriber);
        subscriber.assertFailedWith(NullPointerException.class, "boom");
    }

    @Test
    public void testThatValueIsNotEmittedBeforeSubscription() {
        UniAssertSubscriber<Integer> subscriber = UniAssertSubscriber.create();
        AtomicBoolean called = new AtomicBoolean();
        CompletableFuture<Integer> cs = new CompletableFuture<>();
        cs.complete(1);
        Uni<Integer> uni = Uni.createFrom().future(cs)
                .onItem().invoke(i -> called.set(true));

        assertThat(called).isFalse();

        uni.subscribe().withSubscriber(subscriber);
        subscriber.assertCompleted().assertItem(1);
        assertThat(called).isTrue();
    }

    @Test
    public void testThatValueIsNotEmittedBeforeSubscriptionWithSupplier() {
        UniAssertSubscriber<Integer> subscriber = UniAssertSubscriber.create();
        AtomicBoolean called = new AtomicBoolean();
        CompletableFuture<Integer> cs = new CompletableFuture<>();

        Uni<Integer> uni = Uni.createFrom().future(() -> {
            called.set(true);
            return cs;
        })
                .onItem().invoke(i -> called.set(true));

        assertThat(called).isFalse();

        cs.complete(1);

        assertThat(called).isFalse();

        uni.subscribe().withSubscriber(subscriber);
        subscriber.assertCompleted().assertItem(1);
        assertThat(called).isTrue();
    }

    @Test
    public void testThatSubscriberIsIncompleteIfTheStageDoesNotEmit() {
        UniAssertSubscriber<Integer> subscriber = UniAssertSubscriber.create();
        AtomicBoolean called = new AtomicBoolean();
        CompletableFuture<Integer> cs = new CompletableFuture<>();
        Uni<Integer> uni = Uni.createFrom().future(cs)
                .onItem().invoke(i -> called.set(true));

        assertThat(called).isFalse();
        uni.subscribe().withSubscriber(subscriber);
        assertThat(called).isFalse();
        subscriber.assertNotTerminated();
    }

    @Test
    public void testThatSubscriberIsIncompleteIfTheStageDoesNotEmitFromSupplier() {
        UniAssertSubscriber<Integer> subscriber = UniAssertSubscriber.create();
        AtomicBoolean called = new AtomicBoolean();
        CompletableFuture<Integer> cs = new CompletableFuture<>();
        Uni<Integer> uni = Uni.createFrom().future(() -> cs)
                .onItem().invoke(i -> called.set(true));

        assertThat(called).isFalse();
        uni.subscribe().withSubscriber(subscriber);
        assertThat(called).isFalse();
        subscriber.assertNotTerminated();
    }

    @Test
    public void testThatSubscriberCanCancelBeforeEmission() {
        UniAssertSubscriber<Integer> subscriber = UniAssertSubscriber.create();
        CompletableFuture<Integer> cs = new CompletableFuture<>();
        Uni<Integer> uni = Uni.createFrom().future(cs)
                .onItem().invoke(i -> {
                });

        uni.subscribe().withSubscriber(subscriber);
        subscriber.cancel();

        cs.complete(1);

        subscriber.assertNotTerminated();
    }

    @RepeatedTest(10)
    public void testThatSubscriberCanCancelBeforeEmissionWithSupplier() {
        UniAssertSubscriber<Integer> subscriber = UniAssertSubscriber.create();
        CompletableFuture<Integer> cs = new CompletableFuture<>();
        Uni<Integer> uni = Uni.createFrom().future(() -> cs);
        uni.subscribe().withSubscriber(subscriber);
        subscriber.cancel();

        cs.complete(1);
        subscriber.assertNotTerminated();
    }

    @Test
    public void testThatSubscriberCanCancelAfterEmission() {
        AtomicBoolean called = new AtomicBoolean();
        UniAssertSubscriber<Integer> subscriber = UniAssertSubscriber.create();
        CompletableFuture<Integer> cs = new CompletableFuture<>();
        Uni<Integer> uni = Uni.createFrom().future(cs)
                .onItem().invoke(i -> called.set(true));

        uni.subscribe().withSubscriber(subscriber);
        cs.complete(1);
        subscriber.await();
        subscriber.cancel();
        assertThat(called).isTrue();
        subscriber.assertItem(1);
    }

    @Test
    public void testThatSubscriberCanCancelAfterEmissionWithSupplier() {
        UniAssertSubscriber<Integer> subscriber = UniAssertSubscriber.create();
        CompletableFuture<Integer> cs = new CompletableFuture<>();
        Uni<Integer> uni = Uni.createFrom().future(() -> cs);

        uni.subscribe().withSubscriber(subscriber);
        cs.complete(1);
        subscriber
                .await()
                .cancel();

        subscriber.assertItem(1);
    }

    @Test
    public void testThatCompletionStageCannotBeNull() {
        assertThrows(IllegalArgumentException.class, () -> Uni.createFrom().future((Future<Void>) null));
    }

    @Test
    public void testThatCompletionStageSupplierCannotBeNull() {
        assertThrows(IllegalArgumentException.class,
                () -> Uni.createFrom().future((Supplier<Future<?>>) null));
    }

    @Test
    public void testThatCompletionStageSupplierCannotReturnNull() {
        UniAssertSubscriber<Integer> subscriber = UniAssertSubscriber.create();
        Uni<Integer> uni = Uni.createFrom().future(() -> null);

        uni.subscribe().withSubscriber(subscriber);
        subscriber.assertFailedWith(NullPointerException.class, "");
    }

    @Test
    public void testWithInterruptedFuture() {
        UniAssertSubscriber<String> subscriber = UniAssertSubscriber.create();
        CompletableFuture<String> cs = new CompletableFuture<>();
        Uni.createFrom().future(cs).subscribe().withSubscriber(subscriber);
        cs.cancel(true);
        subscriber
                .await()
                .assertFailedWith(CancellationException.class, null);
    }

}
