import com.github.jargors.sim.*;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.Collections;
import java.util.stream.IntStream;
import java.util.Random;
import java.lang.Math;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
//import javafx.util.Pair;
//import blogspot.software_and_algorithms.stern_library.optimization.HungarianAlgorithm;


//from https://www.techiedelight.com/implement-map-with-multiple-keys-multikeymap-java/
class Key<K1, K2> {
  public K1 key1;
  public K2 key2;

  public Key(K1 key1, K2 key2) {
      this.key1 = key1;
      this.key2 = key2;
  }

  @Override
  public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Key key = (Key) o;

      if (key1 != null ? !key1.equals(key.key1) : key.key1 != null) return false;
      if (key2 != null ? !key2.equals(key.key2) : key.key2 != null) return false;

      return true;
  }

  @Override
  public int hashCode() {
      int result = key1 != null ? key1.hashCode() : 0;
      result = 31 * result + (key2 != null ? key2.hashCode() : 0);
      return result;
  }

  @Override
  public String toString() {
      return "[" + key1 + ", " + key2 + "]";
  }
}

public abstract class MLridesharing extends Client {

  protected final int MAXN = 8;

  //the constant used to indicate the cost of an infeasible insertion (arbitrarily high)
  protected final double COST_INFEASIBLE=100000;

  //Constraints implemented by Simonetto
  //use the same arguments as is used in the controller
  protected int MAX_DT =   // in minutes
      Integer.parseInt(System.getProperty("jargors.controller.max_delay", "7"));
  protected int MAX_WT =   // in minutes
      Integer.parseInt(System.getProperty("jargors.controller.max_wait", "7")); 
  //additional constraint: journey length = twice the length of a vehicle's capacity)

  protected final boolean REBALANCING_ENABLED =
      "true".equals(System.getProperty("jargors.algorithm.rebalance_enable"));

  protected ConcurrentLinkedQueue<int[]> rebalancing_queue = new ConcurrentLinkedQueue<int[]>();

  //we keep a "cache" containing the new routes after insertion of each customer into the route of each vehicle
  //we use a concurrent hash map for easy retrieval and since we're not going to keep all possible customer-vehicle combinations
  ConcurrentHashMap<Key,int[]> cache_b;
  ConcurrentHashMap<Key,int[]> cache_w;

  private final boolean DEBUG =
      "true".equals(System.getProperty("jargors.algorithm.debug"));


  public void init() {
    System.out.printf("Set MAXN=%d\n", MAXN);
    this.batch_processing=true;
    System.out.printf("Set REBALANCING_ENABLED=%b\n", REBALANCING_ENABLED);
    this.cache_w = new ConcurrentHashMap<Key,int[]>();
    this.cache_b = new ConcurrentHashMap<Key,int[]>();
  }

  //used to process a batch of requests rb
  //NOTE: the order of rb must be preserved at all times
  //      example: in the cost matrix and context map, there is one row
  //      for each request in rb
  protected void handleRequestBatch(final int[][] rb) throws ClientException, ClientFatalException {
    if (DEBUG) {
      System.out.printf("handleRequestBatch --> Processing batch of size %d\n", rb.length);
    }

    if (rb.length < 1) { 
      return; 
    }

    try {
      //now that we have a copy of the request batch in rb, we can clear the queue
      //any unserved requests are added back once we know that they cannot be served
      this.queue.clear();

      //1. call context mapping module
      long time_start = System.currentTimeMillis();
      Integer[][] context_map = contextMappingModule(rb);
      long time_end = System.currentTimeMillis();
      if (DEBUG) {
        //System.out.printf("Context Map: \n");
        //for(Integer[] x: context_map)
        //      System.out.println(Arrays.toString(x));  
        System.out.printf("handleRequestBatch --> Context Map (%d ms)\n",(time_end-time_start));
      }

      //getting a unique list of vehicles of interest by first flattening out the context map, and then
      //adding to an array of unique items
      time_start = System.currentTimeMillis();
      int[] vehicle_list=new int[context_map.length*context_map[0].length];
      for (int i = 0; i < context_map.length; i++) {
          // tiny change 1: proper dimensions
          for (int j = 0; j < context_map[0].length; j++) { 
              // tiny change 2: actually store the values
              vehicle_list[(i*context_map[0].length) + j] = context_map[i][j]; 
          }
      }
      time_end = System.currentTimeMillis();
      int[] vehicles = IntStream.of(vehicle_list).distinct().toArray();
      //if (DEBUG) {
      //  System.out.println("Unique list of vehicles\n");
      //  System.out.println(Arrays.toString(vehicles));
      //}

      //2. ask vehicles for insertion costs and form the cost matrix
      time_start = System.currentTimeMillis();
      double[][] cost_matrix = getCostMatrix(rb,vehicles,context_map);
      time_end = System.currentTimeMillis();
      if (DEBUG) {
        //System.out.printf("Insertion Costs: \n");
        //for(double[] x: cost_matrix)
        //      System.out.println(Arrays.toString(x));  
        System.out.printf("handleRequestBatch --> Insertion Costs (%d ms)\n",(time_end-time_start));
      }

      //3. call optimization module
      time_start = System.currentTimeMillis();
      HungarianAlgorithm optModule = new HungarianAlgorithm(cost_matrix);
      int[] assignments = optModule.execute();
      time_end = System.currentTimeMillis();
      if (DEBUG) {
        //System.out.printf("Assignments: \n");
        //System.out.println(Arrays.toString(assignments));  
        System.out.printf("handleRequestBatch --> Hungarian Algorithm (%d ms)\n",(time_end-time_start));
      }
      
      //4. update vehicle routes
      //TODO ensure that the length of the assignments array is equal to the number of requests
      //loop through the request
      //if (DEBUG) {
      //  System.out.printf("Updating Vehicle Routes:\n");
      //}      
      time_start = System.currentTimeMillis();
      for (int i = 0; i < assignments.length; i++ ){
        //the info about the request to be inserted
        //is obtained from rb (recall that the order of "assignments"
        //MUST follow the order of rb)
        final int r_id = rb[i][0];

        //NOTE: the "assignments" array does not hold vehicle IDs which can be 
        //      used directly. It contains the index of that vehicle inside
        //      the "vehicles" array. Therefore, we first fetch that index,
        //      check if it is -1 (which means that the request should not be assigned to any vehicle)
        //      and if not, proceed to fetch the actual vehicle ID and insert it into the route
        int v_index = assignments[i];
        if ((v_index == -1) || (cost_matrix[i][v_index]==this.COST_INFEASIBLE)) {
          //cannot serve request ... rebalance or reject
          if (REBALANCING_ENABLED) {
            //if (DEBUG) {
            //  System.out.printf("Adding request %d (ID %d) to the rebalancing queue\n",i,r_id);
            //}
            this.rebalancing_queue.add(rb[i]);
          }
          else {
            //if (DEBUG) {
            //  System.out.printf("Rejecting request %d (ID %d) since rebalancing is disabled\n",i,r_id);
            //}
          }
          
        }
        else {
          //insert the request into the vehicle's route by fetching the updated route
          //with the new request from the cache
          int v_id = vehicles[v_index];
          //if (DEBUG) {
          //  System.out.printf("Adding request %d (ID %d) to vehicle with ID %d\n",i,r_id,v_id);
          //  System.out.printf("Insertion cost is %f\n",cost_matrix[i][v_index]);
          //}
          Key<Integer,Integer> k_temp=new Key<Integer,Integer>(r_id,v_id);
          final int[] wnew = this.cache_w.get(k_temp);
          //if (DEBUG) {
          //  //TODO remove this part
          //  final int[] wact = this.communicator.queryServerRouteActive(v_id);
          //  System.out.printf("current route: %s\n",Arrays.toString(wact));
          //  
          //  System.out.printf("new route: %s\n",Arrays.toString(wnew));
          //}          
          final int[] bnew = this.cache_b.get(k_temp);
          //if (DEBUG) {
          //  System.out.printf("new schedule: %s\n",Arrays.toString(bnew));
          //}
          try {
            this.communicator.updateServerService(v_id, wnew, bnew, new int[] { r_id }, new int[] { });    
          } catch (Exception e) {
            System.err.println("ERROR occurred during insertion:");
            System.err.println(e.toString());
            e.printStackTrace();
            //add the request back to the queue
            this.queue.add(rb[i]);
          }
          
        }
      }
      time_end = System.currentTimeMillis();
      if (DEBUG) {
        System.out.printf("handleRequestBatch --> Vehicle Route Updates (%d ms)\n",(time_end-time_start));
      }

      if (REBALANCING_ENABLED && !this.rebalancing_queue.isEmpty()) {
        //if (DEBUG) {
        //  System.out.printf("calling reactive rebalancing module\n");
        //}
        time_start = System.currentTimeMillis();
        this.reactiveRebalancingModule();
        time_end = System.currentTimeMillis();
        if (DEBUG) {
          System.out.printf("handleRequestBatch --> Reactive Rebalancing (%d ms)\n",(time_end-time_start));
        }
      }
      //if (DEBUG) {
      //  System.out.printf("---------finished processing batch---------\n");
      //}

    } catch (Exception e) {
      throw new ClientException(e);
    }

  }

  //performs the Context Mapping as described in Simonetto 2019
  protected Integer[][] contextMappingModule(final int[][] rb) throws ClientException, ClientFatalException {

    try {
      Integer [][] contextMapping = new Integer[rb.length][2*MAXN];

      //get the list of available vehicles
      //store a copy of all vehicles and their last recorded times inside "candidates_all"
      Map<Integer, Integer> candidates_all = new HashMap<Integer, Integer>(lut);
     
      //1. filter out vehicles which are full
      // ?? do we need to do this? ("full" depends on the insertion point)
     
      //2. distinguish between vehicles which are idle (i.e. current route is empty)
      //   and those which aren't (i.e. already have scheduled pickups and/or dropoffs)
      List<Integer> candidates_idle = new ArrayList<Integer>();
      List<Integer> candidates_enroute = new ArrayList<Integer>();
      for (final int sid : candidates_all.keySet()) {
        //see comments inside GreedyInsertion.java for the reason why we are 
        //checking if wact[3] is 0 to check whether the vehicle is idle or not
        final int[] wact = this.communicator.queryServerRouteActive(sid);
        if (wact[3] == 0) {
          candidates_idle.add(sid);
        } else {
          candidates_enroute.add(sid);
        }
      }
      //if (DEBUG) {
      //  System.out.printf("Got %d idle vehicles and %d enroute vehicles\n",candidates_idle.size(),candidates_enroute.size());
      //}
  
      //TODO: check what happens if the length of candidates_idle
      //      or candidates_enroute is less than MAXN
  
      //3. for each request:
      for (int r_index = 0; r_index < contextMapping.length; r_index++)
      {
        final int r_origin = rb[r_index][4];

        //a. from the list of idle vehicles, pick the closest "maxn" ones
        final int n_closest_candidates = candidates_idle.size() < MAXN
                              ? candidates_idle.size()
                              : MAXN;
        Integer[] closest_candidates = closest_n_vehicles(r_origin,
                                        (Integer[])candidates_idle.toArray(new Integer[candidates_idle.size()]),
                                        n_closest_candidates);                              
        for (int i1 = 0; i1 < n_closest_candidates; i1++) {
          contextMapping[r_index][i1] = closest_candidates[i1];
        }
        for (int i2 = n_closest_candidates; i2 < MAXN; i2++ ) {
          contextMapping[r_index][i2] = -1;
        }

        //b. from the list of non-idle vehicles, pick "maxn" ones at random
        //TODO: speed up by only shuffling the number of elements we need
        //      (but make sure we don't end up picking duplicates)
        Collections.shuffle(candidates_enroute);
        final int n_random_candidates = candidates_enroute.size() < MAXN
                              ? candidates_enroute.size()
                              : MAXN;
        for (int j1 = 0; j1 < n_random_candidates; j1++) {
          contextMapping[r_index][MAXN+j1] = candidates_enroute.get(j1);
        }
        for (int j2 = MAXN+n_random_candidates; j2 < 2*MAXN; j2++ ) {
          contextMapping[r_index][j2] = -1;
        }
      
      }

      return contextMapping;
      
    } catch (Exception e) {
      throw new ClientException(e);
    }
    
  }

  protected void reactiveRebalancingModule() throws ClientException {
    try{
      //1. fetch list of unassigned requests
      //if (DEBUG) {
      //  System.out.printf("Rebalancing idle vehicles to serve %d customers\n",this.rebalancing_queue.size());
      //}
      int[][] rb = this.rebalancing_queue.toArray(new int[this.rebalancing_queue.size()][7]);
      //now that we have a copy of the request batch in rb, we can clear the queue
      //any unserved requests are added back once we know that they cannot be served
      this.rebalancing_queue.clear();

      //2. fetch list of idle vehicles
      Map<Integer, Integer> candidate_vehicles = new HashMap<Integer, Integer>(lut);
      List<Integer> vehicles_list = new ArrayList<Integer>();
      for (final int sid : candidate_vehicles.keySet()) {
        //see comments inside GreedyInsertion.java for the reason why we are 
        //checking if wact[3] is 0 to check whether the vehicle is idle or not
        final int[] wact = this.communicator.queryServerRouteActive(sid);
        if (wact[3] == 0) {
          vehicles_list.add(sid);
        }
      }
      Integer[] vehicles = vehicles_list.toArray(new Integer[vehicles_list.size()]);
      //if (DEBUG) {
      //  System.out.printf("Got %d idle vehicles to consider for rebalancing\n",vehicles.length);
      //}

      //3. calculate travel distance between each request-vehicle pair
      double [][] cost_matrix = new double[rb.length][vehicles.length];
      for (int r_index = 0; r_index < rb.length; r_index++)
      {
        final int r_origin = rb[r_index][4];
        for (int v_index = 0; v_index < vehicles.length; v_index++) {
          Integer v_id = vehicles[v_index];
          cost_matrix[r_index][v_index] = this.tools.computeHaversine(luv.get(v_id), r_origin);
        }
      }

      //4. solve the assignment problem using the hungarian algorithm
      HungarianAlgorithm optModule = new HungarianAlgorithm(cost_matrix);
      int[] assignments = optModule.execute();
      //if (DEBUG) {
      //  System.out.printf("Rebalancing Assignments: \n");
      //  System.out.println(Arrays.toString(assignments));  
      //  System.out.printf("\n-----------------------\n\n");
      //}

      //5. update vehicle routes
      final int now = this.communicator.retrieveClock();
      for (int r_index = 0; r_index < rb.length; r_index++)
      {
        final int[] r = rb[r_index];
        final int v_index = assignments[r_index];
        final int v_id = vehicles[v_index];
        
        //if (DEBUG) {
        //  System.out.printf("rebalancing vehicle ID %d to location of request ID %d\n",v_id,r[0]);
        //}

        //the remaining schedule will remain the same
        int[] brem = this.communicator.queryServerScheduleRemaining(v_id, now);
        //if (DEBUG) {
        //  System.out.printf("got brem=\n");
        //  for (int __i = 0; __i < (brem.length - 3); __i += 4) {
        //    System.out.printf("  { %d, %d, %d, %d }\n",
        //        brem[__i], brem[__i+1], brem[__i+2], brem[__i+3]);
        //  }
        //}

        //calculate the route from the vehicle's current location to the rebalancing location        
        final int[] wrebalance = this.tools.computeRoute(luv.get(v_id),r[4],now);
        final int l_wrebalance = wrebalance.length;
        //if (DEBUG) {
        //  System.out.printf("calculating route from %d to %d ... got route of length %d\n",luv.get(v_id),r[4],wrebalance.length);
        //  System.out.printf("got wrebalance=\n");
        //  for (int __i = 0; __i < (wrebalance.length - 1); __i += 2) {
        //    System.out.printf("  { %d, %d }\n",
        //    wrebalance[__i], wrebalance[(__i + 1)]);
        //  }
        //}
        
        //we get the vehicle's current route, so that we can merge it with the rebalancing route
        //idle vehicles will always have an ACTIVE route at length 4, with the following elements:
        //wact[0] -> the time at which the vehicle arrived at its location, "t"
        //wact[1] -> the node ID of the vehicle's location
        //wact[2] -> t+1
        //wact[3] -> 0, the node ID of the dummy node
        final int[] wact = this.communicator.queryServerRouteActive(v_id);
        //if (DEBUG) {
        //  System.out.printf("got wact=\n");
        //  for (int __i = 0; __i < (wact.length - 1); __i += 2) {
        //    System.out.printf("  { %d, %d }\n",
        //    wact[__i], wact[(__i + 1)]);
        //  }
        //}

        //we insert wrebalance "in between" wact, with some minor modifications:
        // 1. the first entry in wrebalance is replaced by the first entry from wact
        // 2. we append another entry at the very end, to represent the dummy node
        final int[] wnew = new int[l_wrebalance+2];
        final int l_wnew = wnew.length;
        System.arraycopy(wact,0,wnew,0,2);
        System.arraycopy(wrebalance,2,wnew,2,l_wrebalance-2);        
        wnew[l_wnew-2] = wnew[l_wnew-4]+1;
        wnew[l_wnew-1] = 0;
        //if (DEBUG) {
        //  System.out.printf("got wnew=\n");
        //  for (int __i = 0; __i < (wnew.length - 1); __i += 2) {
        //    System.out.printf("  { %d, %d }\n",
        //    wnew[__i], wnew[(__i + 1)]);
        //  }
        //}
        
        try {
          this.communicator.updateServerService(v_id, wnew, brem, new int[] { }, new int[] { });    
        } catch (Exception e) {
          System.err.println("ERROR occurred during rebalancing route modification:");
          System.err.println(e.toString());
          e.printStackTrace();
          //add the request back to the queue
          this.rebalancing_queue.add(r);
        }
      }
    } catch (Exception e) {
      throw new ClientException(e);
    }
  }

  //TODO benchmark against something found online
  protected Integer[] closest_n_vehicles(final int vertex, final Integer[] vehicle_ids , final int n) throws ClientException, ClientFatalException {
    try{
      Integer[] sorted_ids = new Integer[n];
      Arrays.fill(sorted_ids,-1);//fill with an invalid vehicle ID
      Integer[] sorted_values = new Integer[n];
      Arrays.fill(sorted_values,Integer.MAX_VALUE);//fill with the highest possible value since we want a list of the smallest values

      //for the first entry there's nothing to compare, so we just insert it into the
      //array of sorted items. This is why the following loop starts at 1
      sorted_ids[0]=vehicle_ids[0];
      sorted_values[0]=this.tools.computeHaversine(luv.get(vehicle_ids[0]), vertex);

      //used as a temporary space when swapping two elements during the sort
      Integer tmp_swp=0;

      //loop through the vehicles
      for (int v_index = 1; v_index < vehicle_ids.length; v_index++) {
        //get the vehicle ID and its distance to the vertex of interest
        Integer v_id = vehicle_ids[v_index];
        final int dist = this.tools.computeHaversine(luv.get(v_id), vertex);
        if (dist > 0){//just a sanity check
          
          //we use a bubble-sort like technique (ascending), but the sort is only performed
          //if the calculated value is less than the greatest value in the subset, which will
          //always be at the very end of the array since we maintain a sorted list.
          //this is computationally efficient since n, the length of the subset we want,
          //is usually going to be much smaller than the length of the vehicle id array
          //i.e. as we progress, the "bubble sort" part will happen less and less often,
          //because this if statement won't pass
          if (dist < sorted_values[n-1]) {
            //place the element at the last location
            sorted_ids[n-1]=v_id;
            sorted_values[n-1]=dist;
            //"bubble" through the rest of the array, until we find a value which is less
            //than the current distance. temp_n keeps track of the current location of the entry
            //as it bubbles through the array
            int temp_n=n-1;
            while ( (temp_n>0) && sorted_values[temp_n-1]>sorted_values[temp_n] ) {
              //swap out the values at temp_n and temp_n-1
              tmp_swp=sorted_ids[temp_n];//swapping the IDs
              sorted_ids[temp_n]=sorted_ids[temp_n-1];
              sorted_ids[temp_n-1]=tmp_swp;

              tmp_swp=sorted_values[temp_n];//swapping the values
              sorted_values[temp_n]=sorted_values[temp_n-1];
              sorted_values[temp_n-1]=tmp_swp;
              
              temp_n--;
            }
          }

        }
      }
      return sorted_ids;

    } catch (Exception e) {
      throw new ClientException(e);
    }
  }

  //takes the list of requests and output of the context mapping module to
  //form the cost matrix by asking the vehicles for the insertion cost
  protected double [][] getCostMatrix(final int[][] rb, final int [] vehicles, final Integer[][] contextMapping ) throws ClientException {
    try {
      int num_customers=rb.length;
      int num_vehicles=vehicles.length;

      //building the matrix which will eventually be passed to the matching algorithmbg
      double[][] weight_matrix = new double[num_customers][num_vehicles];
      //looping through the customers
      for (int i = 0; i < num_customers; i++) {
        //looping through the vehicles
        for (int j = 0; j < num_vehicles; j++) {
          int vehicle_id = vehicles[j];

          //check if this vehicle can serve this customer (from the context mapping module)
          if (Arrays.stream(contextMapping[i]).anyMatch(x -> x == vehicle_id) ) {
            weight_matrix[i][j]=getInsertionCost(rb[i], vehicle_id);
          } else {
            //if the vehicle cannot serve this customer, set the weight to infinity 
            weight_matrix[i][j]=this.COST_INFEASIBLE;
          }
        }
      }

      //return padCostMatrix(weight_matrix);
      return weight_matrix;
    } catch (Exception e) {
      throw new ClientException(e);
    }
  }

  //takes a cost matrix and pads it so that it is a square matrix
  protected Integer [][] padCostMatrix(final Integer[][] costMatrix) {
    int num_requests = costMatrix.length; 
    int num_vehicles = costMatrix[0].length;
    int n = Math.max(num_requests, num_vehicles);
    Integer[][] weight_matrix = new Integer[n][n];  

    //padding the cost matrix by iterating through the square matrix and inserting "infinity"
    //whenever there is no corresponding entry in the cost matrix
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        if (i >= num_requests || j >= num_vehicles) {
          weight_matrix[i][j] = Integer.MAX_VALUE;
        }
        else {
          weight_matrix[i][j] = costMatrix[i][j];
        }
      }
    }

    return weight_matrix;
  }

  //takes a request and vehicle id and returns the insertion cost
  protected abstract double getInsertionCost(final int[] r, final int sid) throws ClientException;

}

