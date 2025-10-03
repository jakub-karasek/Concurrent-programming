package cp2024.solution;

import cp2024.circuit.*;
import cp2024.demo.BrokenCircuitValue;

import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ParallelCircuitSolver implements CircuitSolver {
    // Constant specifying the keep-alive time for threads in the pool
    public static final long KEEP_ALIVE_TIME = 60L; // in milliseconds
    private ExecutorService executorService;
    private AtomicBoolean acceptComputations;

    public ParallelCircuitSolver() {
        this.executorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                KEEP_ALIVE_TIME, TimeUnit.MILLISECONDS,
                new SynchronousQueue<>());
        this.acceptComputations = new AtomicBoolean(true);
    }

    @Override
    public CircuitValue solve(Circuit c) {
        // Check if acceptComputations is false, if so return a circuit value that throws an exception
        if (!acceptComputations.get())
            return new ParallelCircuitValue(true);

        Future<Boolean> future = null;

        // Launch callable to calculate value for the root node
        try {
            future = executorService.submit(() -> evaluateValue(c.getRoot()));
            return new ParallelCircuitValue(future);

        } catch (RejectedExecutionException e) {
            // Executor does not accept new submissions
            if (future != null) future.cancel(true);
            return new ParallelCircuitValue(true);

        } catch (Exception e){
            // Handling unexpected exceptions
            if (future != null) future.cancel(true);
            return new ParallelCircuitValue(true);
        }
    }

    @Override
    public void stop() {
        acceptComputations.set(false);
        // Set the interrupted flags for all callables to make them finish their work
        executorService.shutdownNow();
    }

    // Function to calculate the value of the circuit node
    private boolean evaluateValue(CircuitNode n) throws InterruptedException {
        // Checking if the thread has been interrupted
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        // Evaluate leaf node value
        if (n.getType() == NodeType.LEAF)
            return ((LeafNode) n).getValue();

        CircuitNode[] args = n.getArgs();

        int threshold = 0;
        if (n.getType() == NodeType.GT || n.getType() == NodeType.LT) {
            threshold = ((ThresholdNode) n).getThreshold();

            // Check if the value can be determined without calculation
            if (n.getType() == NodeType.LT && threshold <= 0) return false;
            if (n.getType() == NodeType.GT && threshold >= n.getArgs().length) return false;
            if (n.getType() == NodeType.LT && threshold > n.getArgs().length) return true;
        }

        return switch (n.getType()) {
            case IF -> solveIF(args);
            case AND -> solveAOGL(args, args.length, args.length, 0, 0);
            case OR -> solveAOGL(args, 1, args.length, 0, args.length - 1);
            case GT -> solveAOGL(args, threshold + 1, args.length, 0, args.length - threshold - 1);
            case LT -> solveAOGL(args, 0, threshold - 1, args.length - threshold + 1, args.length);
            case NOT -> solveNOT(args);
            default -> throw new RuntimeException("Illegal type " + n.getType());
        };
    }

    // Function to calculate the value of the NOT type circuit node
    private boolean solveNOT(CircuitNode[] args) throws InterruptedException {
        return !evaluateValue(args[0]);
    }

    private Future<Boolean> submitEvaluate(CircuitNode n, ExecutorCompletionService<Boolean> completionService) throws InterruptedException{
        return completionService.submit(() -> evaluateValue(n));
    }

    // Function to lazily calculate the value of the IF type circuit node
    private boolean solveIF(CircuitNode[] args) throws InterruptedException {
        // Create an ExecutionCompletionService based on the global executorService
        ExecutorCompletionService<Boolean> completionService = new ExecutorCompletionService<>(executorService);

        // Create callables to calculate the result for each node argument
        Future<Boolean> condition = null;
        Future<Boolean> tValue = null;
        Future<Boolean> fValue = null;

        try {
            condition = submitEvaluate(args[0], completionService);
            tValue = submitEvaluate(args[1], completionService);
            fValue = submitEvaluate(args[2], completionService);

        } catch (InterruptedException e) {
            // If creating any future fails, cancel the rest and throw an InterruptedException
            if (condition != null) condition.cancel(true);
            if (tValue != null) tValue.cancel(true);
            if (fValue != null) fValue.cancel(true);
            Thread.currentThread().interrupt();
            throw e;
        }

        for (int i = 0; i < 3; i++) {
            // Check if the thread was interrupted before each operation
            if (Thread.currentThread().isInterrupted()) {
                // Interrupt all the callables started by me and throw an InterruptedException
                condition.cancel(true);
                tValue.cancel(true);
                fValue.cancel(true);
                Thread.currentThread().interrupt();
                throw new InterruptedException();
            }

            try {
                // Wait for any callable to complete
                completionService.take();

                // If the condition is calculated, return the appropriate result and cancel unnecessary callables
                if (condition.isDone()) {
                    if (condition.get()) {
                        fValue.cancel(true);
                        return tValue.get();
                    } else {
                        tValue.cancel(true);
                        return fValue.get();
                    }
                }

                // If both result values are calculated and equal, return the result without waiting for the condition
                if (tValue.isDone() && fValue.isDone() && (tValue.get() == fValue.get())) {
                    condition.cancel(true);
                    return tValue.get();
                }

            } catch (InterruptedException | ExecutionException e) {
                // Interrupt all the callables started by me and throw an InterruptedException
                condition.cancel(true);
                tValue.cancel(true);
                fValue.cancel(true);
                throw new InterruptedException();
            }
        }

        try {
            if (condition.get()) return tValue.get();
            return fValue.get();

        } catch (ExecutionException e) {
            throw new InterruptedException();
        }
    }

    // Function to check if we can lazily finish the calculation of the result earlier
    private boolean canReturnEarly(int trueCounter, int falseCounter, int minTrue, int maxTrue, int minFalse, int maxFalse) {
        return trueCounter > maxTrue || falseCounter > maxFalse ||
                (trueCounter >= minTrue && falseCounter >= minFalse);
    }

    // Function to cancel all tasks created by a given thread
    private void cancelAllTasks(ArrayList<Future<Boolean>> taskList) {
        for (Future<Boolean> f : taskList) {
            f.cancel(true);
        }
    }

    // Function to lazily calculate the value of AND, OR, GT, and LT type circuit nodes
    private boolean solveAOGL(CircuitNode[] args, int minTrue, int maxTrue, int minFalse, int maxFalse) throws InterruptedException {
        // Create an ExecutionCompletionService based on the ParallelCircuitSolver's executorService
        ExecutorCompletionService<Boolean> completionService = new ExecutorCompletionService<>(executorService);

        // List of futures created by this thread's callables
        ArrayList<Future<Boolean>> taskList = new ArrayList<>();

        Future<Boolean> future;

        // Create callables for evaluateValue for each child node
        for (CircuitNode c : args) {
            try {
                future = submitEvaluate(c, completionService);

            } catch (InterruptedException e) {
                // If creating any callable fails, cancel all callables
                cancelAllTasks(taskList);
                Thread.currentThread().interrupt();
                throw e;
            }
            taskList.add(future);
        }

        int trueCounter = 0;
        int falseCounter = 0;

        // Receive results in the order the tasks finish
        for (int i = 0; i < args.length; i++) {
            // Check if the isInterrupted flag is set to true
            if (Thread.currentThread().isInterrupted()) {
                // If the thread is interrupted, cancel all my callables and throw an InterruptedException
                cancelAllTasks(taskList);
                Thread.currentThread().interrupt();
                throw new InterruptedException();
            }

            try {
                // Get the calculated future from the queue of completed tasks
                Future<Boolean> calculatedFuture = completionService.take();

                // Update true and false counters
                if (calculatedFuture.get()) trueCounter++;
                else falseCounter++;

                // Check if we can lazily finish the calculation of the result
                if (canReturnEarly(trueCounter, falseCounter, minTrue, maxTrue, minFalse, maxFalse)) {
                    cancelAllTasks(taskList);
                    return trueCounter >= minTrue && falseCounter >= minFalse;
                }

            } catch (InterruptedException | ExecutionException e) {
                cancelAllTasks(taskList);
                Thread.currentThread().interrupt();
                throw new InterruptedException();
            }
        }

        return (!(trueCounter > maxTrue || falseCounter > maxFalse)
                && (trueCounter >= minTrue && falseCounter >= minFalse));
    }
}
