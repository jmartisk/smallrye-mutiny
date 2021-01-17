package io.smallrye.mutiny.infrastructure;

import static io.smallrye.mutiny.helpers.ParameterValidation.nonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.*;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.ParameterValidation;
import io.smallrye.mutiny.subscription.UniSubscriber;
import io.smallrye.mutiny.tuples.Functions;

public class Infrastructure {

    static {
        ServiceLoader<ExecutorConfiguration> executorLoader = ServiceLoader.load(ExecutorConfiguration.class);
        Iterator<ExecutorConfiguration> iterator = executorLoader.iterator();
        if (iterator.hasNext()) {
            ExecutorConfiguration next = iterator.next();
            setDefaultExecutor(nonNull(next.getDefaultWorkerExecutor(), "executor"));
        } else {
            setDefaultExecutor();
        }

        reload();

        resetCanCallerThreadBeBlockedSupplier();
    }

    private static ScheduledExecutorService DEFAULT_SCHEDULER;

    private static Executor DEFAULT_EXECUTOR;
    private static UniInterceptor[] UNI_INTERCEPTORS;
    private static MultiInterceptor[] MULTI_INTERCEPTORS;
    private static CallbackDecorator[] CALLBACK_DECORATORS;
    private static UnaryOperator<CompletableFuture<?>> completableFutureWrapper;
    private static Consumer<Throwable> droppedExceptionHandler = Infrastructure::printAndDump;
    private static BooleanSupplier canCallerThreadBeBlockedSupplier;

    public static void reload() {
        clearInterceptors();
        reloadUniInterceptors();
        reloadMultiInterceptors();
        reloadCallbackDecorators();
    }

    /**
     * Configure or reset the executors.
     */
    public static void setDefaultExecutor() {
        ExecutorService scheduler = ForkJoinPool.commonPool();
        setDefaultExecutor(scheduler);
    }

    public static void setDefaultExecutor(Executor s) {
        if (s == DEFAULT_EXECUTOR) {
            return;
        }
        Executor existing = DEFAULT_EXECUTOR;
        if (existing instanceof ExecutorService) {
            ((ExecutorService) existing).shutdownNow();
        }
        DEFAULT_EXECUTOR = s;
        DEFAULT_SCHEDULER = new MutinyScheduler(s);
    }

    public static ScheduledExecutorService getDefaultWorkerPool() {
        return DEFAULT_SCHEDULER;
    }

    public static Executor getDefaultExecutor() {
        return DEFAULT_EXECUTOR;
    }

    public static <T> Uni<T> onUniCreation(Uni<T> instance) {
        Uni<T> current = instance;
        for (UniInterceptor itcp : UNI_INTERCEPTORS) {
            current = itcp.onUniCreation(current);
        }
        return current;
    }

    public static <T> Multi<T> onMultiCreation(Multi<T> instance) {
        Multi<T> current = instance;
        for (MultiInterceptor interceptor : MULTI_INTERCEPTORS) {
            current = interceptor.onMultiCreation(current);
        }
        return current;
    }

    public static <T> UniSubscriber<? super T> onUniSubscription(Uni<T> instance, UniSubscriber<? super T> subscriber) {
        UniSubscriber<? super T> current = subscriber;
        for (UniInterceptor interceptor : UNI_INTERCEPTORS) {
            current = interceptor.onSubscription(instance, current);
        }
        return current;
    }

    public static <T> Subscriber<? super T> onMultiSubscription(Publisher<? extends T> instance,
            Subscriber<? super T> subscriber) {
        Subscriber<? super T> current = subscriber;
        for (MultiInterceptor itcp : MULTI_INTERCEPTORS) {
            current = itcp.onSubscription(instance, current);
        }
        return current;
    }

    public static <T> Supplier<T> decorate(Supplier<T> supplier) {
        Supplier<T> current = supplier;
        for (CallbackDecorator interceptor : CALLBACK_DECORATORS) {
            current = interceptor.decorate(current);
        }
        return current;
    }

    public static <T> Consumer<T> decorate(Consumer<T> consumer) {
        Consumer<T> current = consumer;
        for (CallbackDecorator interceptor : CALLBACK_DECORATORS) {
            current = interceptor.decorate(current);
        }
        return current;
    }

    public static LongConsumer decorate(LongConsumer consumer) {
        LongConsumer current = consumer;
        for (CallbackDecorator interceptor : CALLBACK_DECORATORS) {
            current = interceptor.decorate(current);
        }
        return current;
    }

    public static Runnable decorate(Runnable runnable) {
        Runnable current = runnable;
        for (CallbackDecorator interceptor : CALLBACK_DECORATORS) {
            current = interceptor.decorate(current);
        }
        return current;
    }

    public static <T1, T2> BiConsumer<T1, T2> decorate(BiConsumer<T1, T2> consumer) {
        BiConsumer<T1, T2> current = consumer;
        for (CallbackDecorator interceptor : CALLBACK_DECORATORS) {
            current = interceptor.decorate(current);
        }
        return current;
    }

    public static <I1, I2, I3, O> Functions.Function3<I1, I2, I3, O> decorate(Functions.Function3<I1, I2, I3, O> function) {
        Functions.Function3<I1, I2, I3, O> current = function;
        for (CallbackDecorator interceptor : CALLBACK_DECORATORS) {
            current = interceptor.decorate(current);
        }
        return current;
    }

    public static <I1, I2, I3, I4, O> Functions.Function4<I1, I2, I3, I4, O> decorate(
            Functions.Function4<I1, I2, I3, I4, O> function) {
        Functions.Function4<I1, I2, I3, I4, O> current = function;
        for (CallbackDecorator interceptor : CALLBACK_DECORATORS) {
            current = interceptor.decorate(current);
        }
        return current;
    }

    public static <I1, I2, I3, I4, I5, O> Functions.Function5<I1, I2, I3, I4, I5, O> decorate(
            Functions.Function5<I1, I2, I3, I4, I5, O> function) {
        Functions.Function5<I1, I2, I3, I4, I5, O> current = function;
        for (CallbackDecorator interceptor : CALLBACK_DECORATORS) {
            current = interceptor.decorate(current);
        }
        return current;
    }

    public static <I1, I2, I3, I4, I5, I6, O> Functions.Function6<I1, I2, I3, I4, I5, I6, O> decorate(
            Functions.Function6<I1, I2, I3, I4, I5, I6, O> function) {
        Functions.Function6<I1, I2, I3, I4, I5, I6, O> current = function;
        for (CallbackDecorator interceptor : CALLBACK_DECORATORS) {
            current = interceptor.decorate(current);
        }
        return current;
    }

    public static <I1, I2, I3, I4, I5, I6, I7, O> Functions.Function7<I1, I2, I3, I4, I5, I6, I7, O> decorate(
            Functions.Function7<I1, I2, I3, I4, I5, I6, I7, O> function) {
        Functions.Function7<I1, I2, I3, I4, I5, I6, I7, O> current = function;
        for (CallbackDecorator interceptor : CALLBACK_DECORATORS) {
            current = interceptor.decorate(current);
        }
        return current;
    }

    public static <I1, I2, I3, I4, I5, I6, I7, I8, O> Functions.Function8<I1, I2, I3, I4, I5, I6, I7, I8, O> decorate(
            Functions.Function8<I1, I2, I3, I4, I5, I6, I7, I8, O> function) {
        Functions.Function8<I1, I2, I3, I4, I5, I6, I7, I8, O> current = function;
        for (CallbackDecorator interceptor : CALLBACK_DECORATORS) {
            current = interceptor.decorate(current);
        }
        return current;
    }

    public static <I1, I2, I3, I4, I5, I6, I7, I8, I9, O> Functions.Function9<I1, I2, I3, I4, I5, I6, I7, I8, I9, O> decorate(
            Functions.Function9<I1, I2, I3, I4, I5, I6, I7, I8, I9, O> function) {
        Functions.Function9<I1, I2, I3, I4, I5, I6, I7, I8, I9, O> current = function;
        for (CallbackDecorator interceptor : CALLBACK_DECORATORS) {
            current = interceptor.decorate(current);
        }
        return current;
    }

    public static <I, O> Function<I, O> decorate(Function<I, O> function) {
        Function<I, O> current = function;
        for (CallbackDecorator interceptor : CALLBACK_DECORATORS) {
            current = interceptor.decorate(current);
        }
        return current;
    }

    public static <I1, I2, O> BiFunction<I1, I2, O> decorate(BiFunction<I1, I2, O> function) {
        BiFunction<I1, I2, O> current = function;
        for (CallbackDecorator interceptor : CALLBACK_DECORATORS) {
            current = interceptor.decorate(current);
        }
        return current;
    }

    public static <T> BinaryOperator<T> decorate(BinaryOperator<T> operator) {
        BinaryOperator<T> current = operator;
        for (CallbackDecorator interceptor : CALLBACK_DECORATORS) {
            current = interceptor.decorate(current);
        }
        return current;
    }

    public static <T1, T2, T3> Functions.TriConsumer<T1, T2, T3> decorate(
            Functions.TriConsumer<T1, T2, T3> consumer) {
        Functions.TriConsumer<T1, T2, T3> current = consumer;
        for (CallbackDecorator interceptor : CALLBACK_DECORATORS) {
            current = interceptor.decorate(current);
        }
        return current;
    }

    public static void setCompletableFutureWrapper(UnaryOperator<CompletableFuture<?>> wrapper) {
        completableFutureWrapper = wrapper;
    }

    @SuppressWarnings("unchecked")
    public static <T> CompletableFuture<T> wrapCompletableFuture(CompletableFuture<T> future) {
        UnaryOperator<CompletableFuture<?>> wrapper = completableFutureWrapper;
        return wrapper != null ? (CompletableFuture<T>) wrapper.apply(future) : future;
    }

    public static void handleDroppedException(Throwable throwable) {
        droppedExceptionHandler.accept(throwable);
    }

    /**
     * Defines a custom caller thread blocking check supplier.
     *
     * @param supplier the supplier, must not be {@code null} and must not throw an exception or it will also be lost.
     */
    public static void setCanCallerThreadBeBlockedSupplier(BooleanSupplier supplier) {
        nonNull(supplier, "supplier");
        canCallerThreadBeBlockedSupplier = supplier;
    }

    public static boolean canCallerThreadBeBlocked() {
        return canCallerThreadBeBlockedSupplier.getAsBoolean();
    }

    /**
     * Defines a custom dropped exception handler.
     * 
     * @param handler the handler, must not be {@code null} and must not throw an exception or it will also be lost.
     */
    public static void setDroppedExceptionHandler(Consumer<Throwable> handler) {
        ParameterValidation.nonNull(handler, "handler");
        droppedExceptionHandler = handler;
    }

    private static void printAndDump(Throwable throwable) {
        System.err.println("[-- Mutiny had to drop the following exception --]");
        StackTraceElement element = Thread.currentThread().getStackTrace()[3];
        System.err.println("Exception received by: " + element.toString());
        throwable.printStackTrace();
        System.err.println("[------------------------------------------------]");
    }

    public static void reloadUniInterceptors() {
        ServiceLoader<UniInterceptor> loader = ServiceLoader.load(UniInterceptor.class);
        List<UniInterceptor> interceptors = new ArrayList<>();
        loader.forEach(interceptors::add);
        interceptors.sort(Comparator.comparingInt(MutinyInterceptor::ordinal));
        UNI_INTERCEPTORS = interceptors.toArray(UNI_INTERCEPTORS);
    }

    public static void reloadMultiInterceptors() {
        ServiceLoader<MultiInterceptor> loader = ServiceLoader.load(MultiInterceptor.class);
        List<MultiInterceptor> interceptors = new ArrayList<>();
        loader.forEach(interceptors::add);
        interceptors.sort(Comparator.comparingInt(MutinyInterceptor::ordinal));
        MULTI_INTERCEPTORS = interceptors.toArray(MULTI_INTERCEPTORS);
    }

    public static void reloadCallbackDecorators() {
        ServiceLoader<CallbackDecorator> loader = ServiceLoader.load(CallbackDecorator.class);
        ArrayList<CallbackDecorator> interceptors = new ArrayList<>();
        loader.forEach(interceptors::add);
        interceptors.sort(Comparator.comparingInt(MutinyInterceptor::ordinal));
        CALLBACK_DECORATORS = interceptors.toArray(CALLBACK_DECORATORS);
    }

    public static void clearInterceptors() {
        UNI_INTERCEPTORS = new UniInterceptor[0];
        MULTI_INTERCEPTORS = new MultiInterceptor[0];
        CALLBACK_DECORATORS = new CallbackDecorator[0];
    }

    // For testing purpose only
    public static void resetDroppedExceptionHandler() {
        droppedExceptionHandler = Infrastructure::printAndDump;
    }

    // For testing purpose only
    public static void resetCanCallerThreadBeBlockedSupplier() {
        canCallerThreadBeBlockedSupplier = () -> true;
    }

    private Infrastructure() {
        // Avoid direct instantiation.
    }

    public static BooleanSupplier decorate(BooleanSupplier supplier) {
        BooleanSupplier current = supplier;
        for (CallbackDecorator interceptor : CALLBACK_DECORATORS) {
            current = interceptor.decorate(current);
        }
        return current;
    }

    public static <T> Predicate<T> decorate(Predicate<T> predicate) {
        Predicate<T> current = predicate;
        for (CallbackDecorator interceptor : CALLBACK_DECORATORS) {
            current = interceptor.decorate(current);
        }
        return current;
    }
}
