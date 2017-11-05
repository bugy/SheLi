package net.buggy.shoplist.utils;


import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

public class ExecutorServiceMonitoringDecorator implements ExecutorService {

    private final ExecutorService delegate;
    private final AtomicLong activeTasksCount = new AtomicLong(0);

    public ExecutorServiceMonitoringDecorator(ExecutorService delegate) {
        this.delegate = delegate;
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @NonNull
    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, @NonNull TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @NonNull
    @Override
    public <T> Future<T> submit(@NonNull Callable<T> task) {
        activeTasksCount.incrementAndGet();

        return delegate.submit(new MonitoredTask<>(task));
    }

    @NonNull
    @Override
    public <T> Future<T> submit(@NonNull Runnable runnable, T result) {
        activeTasksCount.incrementAndGet();

        return delegate.submit(new MonitoredTask<>(runnable, result));
    }

    @NonNull
    @Override
    public Future<?> submit(@NonNull Runnable runnable) {
        activeTasksCount.incrementAndGet();

        return delegate.submit(new MonitoredTask<>(runnable, null));
    }

    @NonNull
    @Override
    public <T> List<Future<T>> invokeAll(@NonNull Collection<? extends Callable<T>> tasks) throws InterruptedException {
        activeTasksCount.addAndGet(tasks.size());

        List<Callable<T>> monitoredTasks = toMonitoredTasks(tasks);

        return delegate.invokeAll(monitoredTasks);
    }

    @NonNull
    private <T> List<Callable<T>> toMonitoredTasks(@NonNull Collection<? extends Callable<T>> tasks) {
        if (tasks.isEmpty()) {
            return Collections.emptyList();
        }

        List<Callable<T>> monitoredTasks = new ArrayList<>(tasks.size());
        for (Callable<T> task : monitoredTasks) {
            monitoredTasks.add(new MonitoredTask<>(task));
        }

        return monitoredTasks;
    }

    @NonNull
    @Override
    public <T> List<Future<T>> invokeAll(@NonNull Collection<? extends Callable<T>> tasks, long timeout, @NonNull TimeUnit unit) throws InterruptedException {
        activeTasksCount.addAndGet(tasks.size());

        List<Callable<T>> monitoredTasks = toMonitoredTasks(tasks);

        return delegate.invokeAll(monitoredTasks, timeout, unit);
    }

    @NonNull
    @Override
    public <T> T invokeAny(@NonNull Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        activeTasksCount.addAndGet(tasks.size());

        List<Callable<T>> monitoredTasks = toMonitoredTasks(tasks);

        return delegate.invokeAny(monitoredTasks);
    }

    @Override
    public <T> T invokeAny(@NonNull Collection<? extends Callable<T>> tasks, long timeout, @NonNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        activeTasksCount.addAndGet(tasks.size());

        List<Callable<T>> monitoredTasks = toMonitoredTasks(tasks);

        return delegate.invokeAny(monitoredTasks, timeout, unit);
    }

    @Override
    public void execute(@NonNull Runnable command) {
        activeTasksCount.incrementAndGet();

        final MonitoredRunnable monitoredRunnable = new MonitoredRunnable(command);

        delegate.execute(monitoredRunnable);
    }

    public boolean isExecuting() {
        return activeTasksCount.get() > 0;
    }

    private final class MonitoredTask<T> implements Callable<T> {
        private final Callable<T> task;

        private final Runnable runnable;
        private final T runnableResult;

        private MonitoredTask(Callable<T> task) {
            this.task = task;
            this.runnableResult = null;
            this.runnable = null;
        }

        public MonitoredTask(Runnable runnable, T result) {
            this.runnable = runnable;
            this.runnableResult = result;
            this.task = null;
        }

        @Override
        public T call() throws Exception {
            try {
                if (task != null) {
                    return task.call();
                } else if (runnable != null) {
                    runnable.run();
                    return runnableResult;
                } else {
                    return runnableResult;
                }
            } finally {
                activeTasksCount.decrementAndGet();
            }
        }
    }

    private final class MonitoredRunnable implements Runnable {

        private final Runnable runnable;

        public MonitoredRunnable(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void run() {
            try {
                if (runnable != null) {
                    runnable.run();
                }
            } finally {
                activeTasksCount.decrementAndGet();
            }
        }
    }
}
