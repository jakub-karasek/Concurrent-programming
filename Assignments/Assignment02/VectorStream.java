package lab03.assignments;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.function.IntBinaryOperator;

public class VectorStream {
    private static final int STREAM_LENGTH = 10;
    private static final int VECTOR_LENGTH = 100;

    /**
     * Function that defines how vectors are computed: the i-th element depends on
     * the previous sum and the index i.
     * The sum of elements in the previous vector is initially given as zero.
     */
    private final static IntBinaryOperator vectorDefinition = (previousSum, i) -> {
        int a = 2 * i + 1;
        return (previousSum / VECTOR_LENGTH + 1) * (a % 4 - 2) * a;
    };

    private static void computeVectorStreamSequentially() {
        int[] vector = new int[VECTOR_LENGTH];
        int sum = 0;
        for (int vectorNo = 0; vectorNo < STREAM_LENGTH; ++vectorNo) {
            for (int i = 0; i < VECTOR_LENGTH; ++i) {
                vector[i] = vectorDefinition.applyAsInt(sum, i);
            }
            sum = 0;
            for (int x : vector) {
                sum += x;
            }
            System.out.println(vectorNo + " -> " + sum);
        }
    }

    private static final CyclicBarrier barrier = new CyclicBarrier(VECTOR_LENGTH, VectorStream::printAndUpdateSum);

    private static final int[] data = new int[VECTOR_LENGTH];
    private static  int sum = 0;
    private static int counter = 0;

    private static void printAndUpdateSum() {
        sum = 0;
        for (int x : data) {
            sum += x;
        }
        System.out.println(counter + " -> " + sum);
        counter++;

    }

    private static class Helper implements Runnable {
        private final int id;

        public Helper(int id) {
            this.id = id;
        }

        @Override
        public void run() {

            try {
                for (int i = 0; i < STREAM_LENGTH; i++){
                    data[id] = vectorDefinition.applyAsInt(sum, id);
                    barrier.await();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();  // Restore the interrupted status
                System.err.println("Thread " + Thread.currentThread().getName() + " was interrupted.");
            } catch (BrokenBarrierException e) {
                System.err.println("Barrier broken in thread " + Thread.currentThread().getName());
            }
        }
    }

    private static void computeVectorStreamInParallel() throws InterruptedException {
        Thread[] threads = new Thread[VECTOR_LENGTH];
        for (int i = 0; i < VECTOR_LENGTH; ++i) {
            threads[i] = new Thread(new Helper(i), "Helper" + i);
        }
        for (int i = 0; i < VECTOR_LENGTH; ++i) {
            threads[i].start();
        }
        try {
            for (int i = 0; i < VECTOR_LENGTH; ++i) {
                threads[i].join();
            }
        } catch (InterruptedException e) {
            // Main thread interrupted, try to interrupt all other threads
            System.err.println("Main thread interrupted. Interrupting all helper threads.");

            // Interrupt all running threads
            for (int i = 0; i < VECTOR_LENGTH; ++i) {
                if (threads[i] != null && threads[i].isAlive()) {
                    threads[i].interrupt();
                }
            }

            // Re-interrupt the main thread to propagate the interruption
            Thread.currentThread().interrupt();
        }

        // Join all threads again to ensure they have stopped
        for (int i = 0; i < VECTOR_LENGTH; ++i) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                System.err.println("Main thread was interrupted again while waiting for threads to finish.");
                Thread.currentThread().interrupt();  // Propagate interruption again
            }
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("-- Sequentially --");
            computeVectorStreamSequentially();
            System.out.println("-- Parallel --");
            computeVectorStreamInParallel();
            System.out.println("-- End --");
        } catch (InterruptedException e) {
            System.err.println("Main interrupted.");
        }
    }
}
