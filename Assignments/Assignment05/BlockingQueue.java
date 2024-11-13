package lab06.assignments;

import java.util.LinkedList;
import java.util.Queue;

public class BlockingQueue<T> {
    private final int capacity;
    private final Queue<T> queue;

    private T randez_vouz_result;

    public BlockingQueue(int capacity) {
        this.capacity = capacity;
        this.queue = new LinkedList<>();
        this.randez_vouz_result = null;
    }

    public synchronized T take() throws InterruptedException {
        // special case for queue with size 0
        if (capacity == 0){
            while (this.randez_vouz_result == null){
                wait();
            }
            T result = randez_vouz_result;
            randez_vouz_result = null;
            notifyAll();
            return result;
        }

        while (queue.isEmpty()) {
            wait();
        }
        T result = queue.poll();
        notifyAll();
        return result;
    }

    public synchronized void put(T item) throws InterruptedException {
        // special case for queue with size 0
        if (capacity == 0){
            while (randez_vouz_result != null){
                wait();
            }
            randez_vouz_result = item;
            wait();
            return;
        }

        while (queue.size() == capacity) {
            wait();
        }
        queue.offer(item);
        notifyAll();

    }

    public synchronized int getSize() {

        return queue.size();
    }

    public int getCapacity() {
        return capacity;
    }
}