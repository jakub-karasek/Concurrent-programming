package lab04.assignments;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.IntBinaryOperator;

public class MatrixRowSums {
    private static final int N_ROWS = 10;
    private static final int N_COLUMNS = 100;

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

    private static void printRowSumsInParallel() throws InterruptedException {
        List<Thread> threads = new ArrayList<>();

        // Hashmap containing row records
        // key - number of row
        // value - pair of Long adders, first contains number of columns calculated and second the partial result
        ConcurrentHashMap<Integer, Map.Entry<LongAdder, LongAdder>> map = new ConcurrentHashMap<>();

        for (int c = 0; c < N_COLUMNS; ++c) {
            final int myColumn = c;
            threads.add(new Thread(() -> {
                int collumn = myColumn;
                for (int row = 0; row < N_ROWS; row++){
                    /// Adds new row record if it's not in the map
                    map.putIfAbsent(row, new AbstractMap.SimpleEntry<>(new LongAdder(), new LongAdder()));

                    // Increment the number of columns calculated and update sum
                    int finalRow = row;
                    map.computeIfPresent(row, (key, value) -> {
                        // Increment and update sum
                        value.getKey().increment();
                        value.getValue().add(matrixDefinition.applyAsInt(finalRow, myColumn));

                        // Check if all columns have been calculated
                        if (value.getKey().sum() == N_COLUMNS) {
                            System.out.println(finalRow + " -> " + value.getValue().sum());
                            // Return null to remove the entry
                            return null;
                        }
                        return value; // Return the updated value if not all columns calculated
                    });

                }
            }));
        }
        for (Thread t : threads) {
            t.start();
        }

        try {
            for (Thread t : threads) {
                t.join();
            }

        } catch (InterruptedException e) {
            for (Thread t : threads) {
                t.interrupt();
            }
            throw e;
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("-- Sequentially --");
            printRowSumsSequentially();
            System.out.println("-- In parallel --");
            printRowSumsInParallel();
            System.out.println("-- End --");
        } catch (InterruptedException e) {
            System.err.println("Main interrupted.");
        }
    }
}
