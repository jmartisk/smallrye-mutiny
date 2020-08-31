package io.smallrye.mutiny.operators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.reactivex.Flowable;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.test.AssertSubscriber;

public class MultiCreateFromPublisherTest {

    @Test
    public void testThatPublisherCannotBeNull() {
        assertThrows(IllegalArgumentException.class, () -> Multi.createFrom().publisher(null));
    }

    @Test
    public void testWithFailedPublisher() {
        AssertSubscriber<String> subscriber = Multi.createFrom().<String> publisher(
                Flowable.error(new IOException("boom"))).subscribe()
                .withSubscriber(AssertSubscriber.create());
        subscriber.assertHasFailedWith(IOException.class, "boom");
    }

    @Test
    public void testWithEmptyPublisher() {
        AssertSubscriber<String> subscriber = Multi.createFrom().<String> publisher(Flowable.empty()).subscribe()
                .withSubscriber(AssertSubscriber.create());
        subscriber.assertCompletedSuccessfully().assertHasNotReceivedAnyItem();
    }

    @Test
    public void testWithRegularPublisher() {
        AtomicLong requests = new AtomicLong();
        AtomicInteger count = new AtomicInteger();
        Flowable<Integer> flowable = Flowable.defer(() -> {
            count.incrementAndGet();
            return Flowable.just(1, 2, 3, 4);
        }).doOnRequest(requests::addAndGet);

        Multi<Integer> multi = Multi.createFrom().publisher(flowable);

        multi.subscribe().withSubscriber(AssertSubscriber.create()).assertHasNotReceivedAnyItem()
                .request(2)
                .assertReceived(1, 2)
                .run(() -> assertThat(requests).hasValue(2))
                .request(1)
                .assertReceived(1, 2, 3)
                .request(1)
                .assertReceived(1, 2, 3, 4)
                .run(() -> assertThat(requests).hasValue(4))
                .assertCompletedSuccessfully();

        assertThat(count).hasValue(1);

        multi.subscribe().withSubscriber(AssertSubscriber.create()).assertHasNotReceivedAnyItem()
                .request(2)
                .assertReceived(1, 2)
                .request(1)
                .assertReceived(1, 2, 3)
                .request(1)
                .assertReceived(1, 2, 3, 4)
                .run(() -> assertThat(requests).hasValue(8))
                .assertCompletedSuccessfully();

        assertThat(count).hasValue(2);

    }

    @Test
    public void testThatCancellingTheMultiCancelThePublisher() {
        AtomicBoolean cancellation = new AtomicBoolean();
        Flowable<Integer> flowable = Flowable.just(1, 2, 3, 4).doOnCancel(() -> cancellation.set(true));

        Multi<Integer> multi = Multi.createFrom().publisher(flowable);

        multi.subscribe().withSubscriber(AssertSubscriber.create()).assertHasNotReceivedAnyItem()
                .request(2)
                .assertReceived(1, 2)
                .run(() -> assertThat(cancellation).isFalse())
                .request(1)
                .assertReceived(1, 2, 3)
                .cancel()
                .request(1)
                .assertReceived(1, 2, 3)
                .assertNotTerminated();

        assertThat(cancellation).isTrue();
    }
}
