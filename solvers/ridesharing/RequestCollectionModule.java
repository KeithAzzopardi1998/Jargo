package ridesharing;
import java.util.concurrent.ConcurrentLinkedQueue;

public interface RequestCollectionModule {
    public int[][] getRequests();
    
    class ImmediateRCM implements RequestCollectionModule {
        public ConcurrentLinkedQueue<int[]> immediate_queue;

        public ImmediateRCM(ConcurrentLinkedQueue<int[]> q_in) {
            immediate_queue = q_in;
        }

        public int[][] getRequests() {
            int[][] rb = this.immediate_queue.toArray(new int[this.immediate_queue.size()][7]);

            //now that we have a copy of the request batch in rb, we can clear the queue
            //any unserved requests are added back once we know that they cannot be served
            this.immediate_queue.clear();
            
            return rb;
        }
    }

    class ImmediateAndSampledRCM implements RequestCollectionModule {
        public int[][] getRequests() {
            return new int[10][10];
        }
    }    

}
