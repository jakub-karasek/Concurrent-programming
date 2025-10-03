package cp2024.solution;

import cp2024.circuit.CircuitValue;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class ParallelCircuitValue implements CircuitValue {
    boolean isInterrupted;
    boolean isCalculated;
    Future<Boolean> future;
    Boolean value;

    public ParallelCircuitValue(Future<Boolean> future) {
        this.isCalculated = false;
        this.isInterrupted = false;
        this.future = future;
    }

    public ParallelCircuitValue(boolean isInterrupted) {
        this.isInterrupted = isInterrupted;
    }

    @Override
    public boolean getValue() throws InterruptedException {
        // If interrupted, throw InterruptedException
        if (isInterrupted) throw new InterruptedException();

        // If the future is cancelled, the computation of the result has been stopped,
        // so throw InterruptedException
        if (future.isCancelled()) {
            isInterrupted = true;
            throw new InterruptedException();
        }

        // If the value has not yet been calculated, call future.get and wait for the result
        if (!isCalculated) {
            try {
                value = future.get();
                isCalculated = true;
            } catch (ExecutionException e) {
                throw new InterruptedException();
            }
        }

        // At this point isCalculated is true, so the value has been computed, and it can be returned
        return value;
    }
}
