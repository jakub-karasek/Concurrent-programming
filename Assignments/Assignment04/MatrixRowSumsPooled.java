package lab05.assignments;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntBinaryOperator;


public class MatrixRowSumsPooled {
    private static final int N_ROWS = 10;
    private static final int N_COLUMNS = 100;
    private static final int N_THREADS = 4;

    private static IntBinaryOperator matrixDefinition = (row, col) -> {
        int a = 2 * col + 1;
        return (row + 1) * (a % 4 - 2) * a;
    };

    private static void printRowSumsSequentially() {
        for (int r = 0; r < N_ROWS; ++r) {
            int sum = 0;
            for (int c = 0; c < N_COLUMNS; ++c) {
                sum += matrixDefinition.applyAsInt(r, c);
            }
            System.out.println(r + " -> " + sum);
        }
    }

    public static void printRowSumsInParallel() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(N_THREADS);
        List<Callable<Integer>> tasks = new ArrayList<>();
        try {
            for (int r = 0; r < N_ROWS; r++) {
                // Create a task for each cell in the row
                for (int c = 0; c < N_COLUMNS; ++c) {
                    tasks.add(new Task(r, c));
                }

                // Execute all tasks and create a list of futures
                List<Future<Integer>> futures = pool.invokeAll(tasks);
                int sum = 0;

                // Sum the results
                for (Future<Integer> futureResult : futures) {
                    sum += futureResult.get();
                }

                // Print the sum of the row and clear the task queue
                System.out.println(r + " -> " + sum);
                tasks.clear();
            }

        } catch (InterruptedException e) {
            System.err.println("Calculations interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            System.err.println("Execution failed: " + e.getCause().getMessage());
        } finally {
            pool.shutdown();
        }
    }


    private static class Task implements Callable<Integer> {
        private final int rowNo;
        private final int columnNo;

        private Task(int rowNo, int columnNo) {
            this.rowNo = rowNo;
            this.columnNo = columnNo;
        }

        @Override
        public Integer call() {
            return matrixDefinition.applyAsInt(rowNo, columnNo);
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("-- Sequentially --");
            printRowSumsSequentially();
            System.out.println("-- In a FixedThreadPool --");
            printRowSumsInParallel();
            System.out.println("-- End --");
        } catch (InterruptedException e) {
            System.err.println("Main interrupted.");
        }
    }
}
