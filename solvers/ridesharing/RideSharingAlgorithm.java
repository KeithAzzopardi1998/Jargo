package ridesharing;
import com.github.jargors.sim.*;

import ridesharing.DemandPredictionModule;

import java.util.stream.IntStream;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

//this class handles the high-level communication between modules
public class RideSharingAlgorithm extends Client {
  
  //declaring the modules we will be using
  private ContextMappingModule cmm;
  private CostComputationModule ccm;
  private DemandPredictionModule dpm;
  private OptimizationModule om;
  private PathComputationModule pcm;
  private RequestCollectionModule rcm;

  //the constant used to indicate the cost of an infeasible insertion (arbitrarily high)
  protected final double COST_INFEASIBLE=100000;

  //we keep a "cache" containing the new routes after insertion of each customer into the route of each vehicle
  //we use a concurrent hash map for easy retrieval and since we're not going to keep all possible customer-vehicle combinations
  ConcurrentHashMap<Key<Integer,Integer>,int[]> cache_b;
  ConcurrentHashMap<Key<Integer,Integer>,int[]> cache_w;

    //Constraints implemented by Simonetto
  //use the same arguments as is used in the controller
  protected int MAX_DT =   // property is in minutes, but convert to seconds here
      Integer.parseInt(System.getProperty("jargors.controller.max_delay", "7")) * 60;
  protected int MAX_WT =   // property is in minutes, but convert to seconds here
      Integer.parseInt(System.getProperty("jargors.controller.max_wait", "7")) * 60; 
  //additional constraint: journey length = twice the length of a vehicle's capacity)


  private final boolean DEBUG =
      "true".equals(System.getProperty("jargors.algorithm.debug"));

  private final boolean REBALANCING_ENABLED =
      "true".equals(System.getProperty("jargors.algorithm.rebalance_enable"));

  private final String VARIANT = System.getProperty("jargors.algorithm.variant");
  private final String DEMAND_MODEL_TYPE = System.getProperty("jargors.algorithm.dm_type");
  private final int DEMAND_MODEL_HEIGHT = Integer.parseInt(System.getProperty("jargors.algorithm.dm_height"));
  private final int DEMAND_MODEL_WIDTH = Integer.parseInt(System.getProperty("jargors.algorithm.dm_width"));
    
  //max number of vehicles to be considered by the context mapping
  //module (per request), as defined by Simonetto
  protected final int MAXN =
      Integer.parseInt(System.getProperty("jargors.algorithm.maxn", "8"));

  protected ConcurrentLinkedQueue<int[]> rebalancing_queue = new ConcurrentLinkedQueue<int[]>();
  public void init() {
    System.out.printf("Set MAXN=%d\n", MAXN);
    System.out.printf("Set REBALANCING_ENABLED=%b\n", REBALANCING_ENABLED);
    System.out.printf("Set DEMAND_MODEL_TYPE=%s\n", DEMAND_MODEL_TYPE);
    System.out.printf("Set VARIANT=%s\n", VARIANT);    
    this.batch_processing=true;
    cache_w = new ConcurrentHashMap<Key<Integer,Integer>,int[]>();
    cache_b = new ConcurrentHashMap<Key<Integer,Integer>,int[]>();

    //initializing common modules with the desired parameters
    cmm = new ContextMappingModule(MAXN);
    ccm = new CostComputationModule(COST_INFEASIBLE, false, MAX_WT, MAX_DT);
    om  = new OptimizationModule();
    
    //setting the modules which defer between algorithms
    if (VARIANT.equals("baseline")) {
      rcm = new RequestCollectionModule.ImmediateRCM(this.queue);
      pcm = new PathComputationModule.ShortestPCM();
      //the baseline algorithm does not use predicted demand
      dpm = null;
      this.DM_ENABLE = false;
    }
    else {
      //setting the demand model
      this.DM_ENABLE = true;
      if (DEMAND_MODEL_TYPE.equals("dnn")) {
        dpm  = new DNNModel(DEMAND_MODEL_HEIGHT, DEMAND_MODEL_WIDTH, false, this.communicator);
      } else if (DEMAND_MODEL_TYPE.equals("frequentist")) {
        dpm  = new FrequentistModel(DEMAND_MODEL_HEIGHT, DEMAND_MODEL_WIDTH, false, this.communicator);
      }

      //setting the other models
      if (VARIANT.equals("sampling")) {
        rcm = new RequestCollectionModule.ImmediateAndSampledRCM();
        pcm = new PathComputationModule.ShortestPCM();
      }
      else if (VARIANT.equals("routing")) {
        rcm = new RequestCollectionModule.ImmediateRCM(this.queue);
        pcm = new PathComputationModule.MaxScorePCM();
      }
    }
  }

  //used to process a batch of requests rb
  //NOTE: the order of rb must be preserved at all times
  //      example: in the cost matrix and context map, there is one row
  //      for each request in rb
  protected void handleRequestBatch() throws ClientException, ClientFatalException {
    try{

      //TODO
      //1. fetch requests to process
      int[][] rb = rcm.getRequests();
      if (DEBUG) {
        System.out.printf("handleRequestBatch --> Processing batch of size %d\n", rb.length);
      }

      if (rb.length < 1) { 
        return; 
      }

      //2. call context mapping module
      long time_start = System.currentTimeMillis();
      Integer[][] context_map = cmm.getContextMap(rb, this.communicator, this.tools, this.lut, this.luv);
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

      //3. ask vehicles for insertion costs and form the cost matrix
      time_start = System.currentTimeMillis();
      double[][] cost_matrix = ccm.getCostMatrix(rb,vehicles,context_map, this.communicator, this.tools, this.lut, this.luv, this.cache_w, this.cache_b, this.pcm);
      time_end = System.currentTimeMillis();
      if (DEBUG) {
        System.out.printf("handleRequestBatch --> Insertion Costs (%d ms)\n",(time_end-time_start));
      }

      //4. call the optimization model on the cost matrix
      time_start = System.currentTimeMillis();
      int[] assignments = om.getAssignments(cost_matrix);
      time_end = System.currentTimeMillis();
      if (DEBUG) {
        //System.out.printf("Assignments: \n");
        //System.out.println(Arrays.toString(assignments));  
        System.out.printf("handleRequestBatch --> Hungarian Algorithm (%d ms)\n",(time_end-time_start));
      }      

      //4. update vehicle routes
      time_start = System.currentTimeMillis();
      updateVehicleRoutes(rb,assignments,cost_matrix,vehicles);
      time_end = System.currentTimeMillis();
      if (DEBUG) {
        System.out.printf("handleRequestBatch --> Vehicle Route Updates (%d ms)\n",(time_end-time_start));
      }

      //5. rebalance empty vehicles
      if (REBALANCING_ENABLED && !this.rebalancing_queue.isEmpty()) {
        //if (DEBUG) {
        //  System.out.printf("calling reactive rebalancing module\n");
        //}
        time_start = System.currentTimeMillis();
        rebalanceVehicles();
        time_end = System.currentTimeMillis();
        if (DEBUG) {
          System.out.printf("handleRequestBatch --> Reactive Rebalancing (%d ms)\n",(time_end-time_start));
        }
      }

    } catch (Exception e) {
      throw new ClientException(e);
    }
  }
  
  private void updateVehicleRoutes(int[][]rb, int[] assignments, double[][] cost_matrix, int[] vehicles) {
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
          //cannot serve request ... rebalance or reject
          if (REBALANCING_ENABLED) {
            //if (DEBUG) {
            //  System.out.printf("Adding request %d (ID %d) to the rebalancing queue\n",i,r_id);
            //}
            this.rebalancing_queue.add(rb[i]);
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
  }

  private void rebalanceVehicles() throws ClientException {
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
      Map<Integer, Integer> candidate_vehicles = new HashMap<Integer, Integer>(this.lut);
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

      //4. solve the assignment problem using the optimization module
      int[] assignments = om.getAssignments(cost_matrix);
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
        final int[] wrebalance = this.pcm.getPath(this.luv.get(v_id),r[4],now, this.tools);
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
  public void updateDemandModelPredictions() throws ClientException {
    try {
      this.dpm.runDemandModel();
    } catch (Exception e) {
      throw new ClientException(e);
    }
  }
}
