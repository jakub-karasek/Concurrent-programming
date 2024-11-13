package lab06.assignments;

// If your solution is not in the same package as specified above, import it.
// import lab06.solutions.BlockingQueue;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class BlockingQueueTest {
    private static boolean testQueue() {
        int capacity = 3;
        BlockingQueue<Character> blockingQueue = new BlockingQueue<>(capacity);
        BlockingQueue<Character> testingQueue = new BlockingQueue<>(2);

        List<Thread> threads = new ArrayList<>();

        threads.add(new Thread(() -> {
            try {
                assertThat(blockingQueue.getSize() == 0, "Expected empty queue (pre).");
                for (int i = 0; i < capacity + 1; i++) {
                    blockingQueue.put((char) ('A' + i));
                }
                // The last put was above capacity,
                // so it should wait until the other thread starts to take().
                // By then, the other thread should have put 'X' on testingQueue.
                testingQueue.put('Y');
                int s = testingQueue.getSize();
                assertThat(s == 2, String.format("Expected size 2, got: %c", s));
                char a = testingQueue.take();
                assertThat(a == 'X', String.format("Expected 'X', got: %c", a));

                testingQueue.put('Z');

                // Put the very last element to blockingQueue.
                blockingQueue.put((char) ('A' + capacity + 1));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }));

        threads.add(new Thread(() -> {
            try {
                Thread.sleep(50);
                testingQueue.put('X');
                for (int i = 0; i < capacity + 2; i++) {
                    blockingQueue.take();
                }
                // The last take above should wait until the other thread puts its very last
                // element to blockingQueue.
                // By then, it should have taken 'X' from testingQueue, leaving 'Y' and 'Z'.
                char a = testingQueue.take();
                assertThat(a == 'Y', String.format("Expected 'Y', got: %c", a));
                a = testingQueue.take();
                assertThat(a == 'Z', String.format("Expected 'Z', got: %c", a));
                assertThat(testingQueue.getSize() == 0, "Expected empty queue (post).");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }));

        return testThreads(Duration.ofMillis(300), threads);
    }

    private static boolean testMultipleProducersConsumers() {
        int capacity = 2;
        int producersConsumersCount = 100;
        BlockingQueue<Integer> blockingQueue = new BlockingQueue<>(capacity);

        List<Thread> threads = new ArrayList<>();

        Runnable producer = () -> {
            try {
                for (int i = 0; i < capacity + 1; i++) {
                    blockingQueue.put(i);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        Runnable consumer = () -> {
            try {
                for (int i = 0; i < capacity + 1; i++) {
                    blockingQueue.take();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        for (int i = 0; i < producersConsumersCount; i++) {
            threads.add(new Thread(producer));
            threads.add(new Thread(consumer));
        }

        return testThreads(Duration.ofMillis(500), threads);
    }

    private static boolean testRendezvous() {
        BlockingQueue<Character> blockingQueue = new BlockingQueue<>(0);
        Duration firstSleepTime = Duration.ofMillis(200);
        Duration secondSleepTime = Duration.ofMillis(400);
        Instant expectedReadyTime = Instant.now().plus(firstSleepTime).plus(secondSleepTime);

        List<Thread> threads = new ArrayList<>();

        threads.add(new Thread(() -> {
            try {
                assertThat(blockingQueue.getSize() == 0, "Expected empty queue (pre A).");
                blockingQueue.put('Q');
                // This thread will sleep until the other one receives the value.
                assertThat(blockingQueue.getSize() == 0, "Expected empty queue (post A).");
                assertThat(Instant.now().isAfter(expectedReadyTime), "Expected to wait until other thread ready.");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }));

        threads.add(new Thread(() -> {
            try {
                Thread.sleep(firstSleepTime.toMillis());
                assertThat(blockingQueue.getSize() == 0, "Expected empty queue (pre B).");
                Thread.sleep(secondSleepTime.toMillis());
                assertThat(blockingQueue.take() == 'Q', "Expected a different value.");
                assertThat(blockingQueue.getSize() == 0, "Expected empty queue (post B).");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }));

        return testThreads(Duration.ofMillis(1000), threads);
    }

    private static void assertThat(boolean predicate, String message) {
        // Not using built-in `assert`, because it's disabled by default (even in debug)
        // and tedious to enable (pass the -enableassertions or -ea flag to the JVM).
        if (!predicate) {
            throw new AssertionError(message);
        }
    }

    // Start threads and check whether they finish in time and without exceptions.
    private static boolean testThreads(Duration timeout, List<Thread> threads) {
        AtomicBoolean ok = new AtomicBoolean(true);

        for (Thread t : threads) {
            t.setUncaughtExceptionHandler((thread, exception) -> {
                ok.set(false);
                exception.printStackTrace();
            });
        }

        for (Thread t : threads) {
            t.start();
        }

        try {
            Thread.sleep(timeout.toMillis());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        for (Thread t : threads) {
            if (t.isAlive()) {
                System.out.println("Timeout on thread: " + t);
                t.interrupt();
                ok.set(false);
            }
        }

        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (ok.get()) {
            System.out.println("OK");
            return true;
        } else {
            System.out.println("FAIL");
            return false;
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            System.out.println("== Test run " + (i + 1) + " ==");
            if (!testQueue())
                break;
            if (!testMultipleProducersConsumers())
                break;
            if (!testRendezvous())
                 break;
        }
    }

}
