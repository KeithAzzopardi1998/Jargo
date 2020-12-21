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
  //final int MAX_PROXIMITY = 1800;
  final int MAXN = 8;

  //the constant used to indicate the cost of an infeasible insertion (arbitrarily high)
  final double COST_INFEASIBLE=100000;

  //we keep a "cache" containing the new routes after insertion of each customer into the route of each vehicle
  //we use a concurrent hash map for easy retrieval and since we're not going to keep all possible customer-vehicle combinations
  ConcurrentHashMap<Key,int[]> cache_b;
  ConcurrentHashMap<Key,int[]> cache_w;

  protected final boolean DEBUG =
      "true".equals(System.getProperty("jargors.algorithm.debug"));


  public void init() {
    System.out.printf("Set MAXN=%d\n", MAXN);
    this.batch_processing=true;
    this.cache_w = new ConcurrentHashMap<Key,int[]>();
    this.cache_b = new ConcurrentHashMap<Key,int[]>();
  }

  //used to process a batch of requests rb
  //NOTE: the order of rb must be preserved at all times
  //      example: in the cost matrix and context map, there is one row
  //      for each request in rb
  protected void handleRequestBatch(final int[][] rb) throws ClientException, ClientFatalException {
    if (DEBUG) {
      System.out.printf("processing batch of size %d\n", rb.length);
    }

    if (rb.length < 1) { 
      return; 
    }

    try {
      //now that we have a copy of the request batch in rb, we can clear the queue
      //any unserved requests are added back once we know that they cannot be served
      this.queue.clear();

      //1. call context mapping module
      Integer[][] context_map = contextMappingModule(rb);
      if (DEBUG) {
        System.out.printf("Context Map: \n");
        for(Integer[] x: context_map)
              System.out.println(Arrays.toString(x));  
        System.out.printf("\n-----------------------\n\n");
      }

      //getting a unique list of vehicles of interest by first flattening out the context map, and then
      //adding to an array of unique items
      int[] vehicle_list=new int[context_map.length*context_map[0].length];
      for (int i = 0; i < context_map.length; i++) {
          // tiny change 1: proper dimensions
          for (int j = 0; j < context_map[0].length; j++) { 
              // tiny change 2: actually store the values
              vehicle_list[(i*context_map[0].length) + j] = context_map[i][j]; 
          }
      }
      int[] vehicles = IntStream.of(vehicle_list).distinct().toArray();
      if (DEBUG) {
        System.out.println("Unique list of vehicles\n");
        System.out.println(Arrays.toString(vehicles));
        System.out.printf("\n-----------------------\n\n");
      }

      //2. ask vehicles for insertion costs and form the cost matrix
      double[][] cost_matrix = getCostMatrix(rb,vehicles,context_map);
      if (DEBUG) {
        System.out.printf("Insertion Costs: \n");
        for(double[] x: cost_matrix)
              System.out.println(Arrays.toString(x));  
        System.out.printf("\n-----------------------\n\n");
      }

      //3. call optimization module
      HungarianAlgorithm optModule = new HungarianAlgorithm(cost_matrix);
      int[] assignments = optModule.execute();
      if (DEBUG) {
        System.out.printf("Assignments: \n");
        System.out.println(Arrays.toString(assignments));  
        System.out.printf("\n-----------------------\n\n");
      }
      
      //4. update vehicle routes
      //TODO ensure that the length of the assignments array is equal to the number of requests
      //loop through the request
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
          //add the request back to the queue so that we can try to insert it in the next epoch
          if (DEBUG) {
            System.out.printf("Adding request %d (ID %d) back to the queue\n",i,r_id);
          }
          this.queue.add(rb[i]);
          //TODO: this is where the reactive repositioning should probably come in too
        }
        else {
          //insert the request into the vehicle's route by fetching the updated route
          //with the new request from the cache
          int v_id = vehicles[v_index];
          if (DEBUG) {
            System.out.printf("Adding request %d (ID %d) to vehicle with ID %d\n",i,r_id,v_id);
            System.out.printf("Insertion cost is %f\n",cost_matrix[i][v_index]);
          }
          Key<Integer,Integer> k_temp=new Key<Integer,Integer>(r_id,v_id);
          final int[] wnew = this.cache_w.get(k_temp);
          if (DEBUG) {
            System.out.printf("new route: %s\n",Arrays.toString(wnew));
          }          
          final int[] bnew = this.cache_b.get(k_temp);
          if (DEBUG) {
            System.out.printf("new schedule: %s\n",Arrays.toString(bnew));
          }
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
      if (DEBUG) {
        System.out.printf("---------finished processing batch---------\n");
      }

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
      if (DEBUG) {
        System.out.printf("Got %d idle vehicles and %d enroute vehicles\n",candidates_idle.size(),candidates_enroute.size());
      }
  
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
  
      
      //for now, we will just fill in the context mapping block with random values
      /*
      Integer[] random_values= new Integer[contextMapping[0].length + 5];
      for (int i = 0; i < random_values.length; i++) {
        random_values[i] = i;
      }
      List<Integer> random_values_list = Arrays.asList(random_values);
      for (int r = 0; r < contextMapping.length; r++) {
        Collections.shuffle(random_values_list);
        random_values_list.toArray(random_values);
        contextMapping[r] = Arrays.copyOfRange(random_values,0,contextMapping[0].length);
      }
      */
      return contextMapping;
      
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

