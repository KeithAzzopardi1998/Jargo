package com.github.jargors.client;
import com.github.jargors.sim.*;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.Collections;
import java.util.stream.IntStream;
import java.util.Random;
import java.lang.Math;
import blogspot.software_and_algorithms.stern_library.optimization.HungarianAlgorithm;

public class MLridesharing extends Client {
  //final int MAX_PROXIMITY = 1800;
  final int MAXN = 8;

  public void init() {
    System.out.printf("Set MAX_PROXIMITY=%d\n", MAX_PROXIMITY);
    this.batch_processing=true;
  }

  //used to process a batch of requests rb
  //NOTE: the order of rb must be preserved at all times
  //      example: in the cost matrix and context map, there is one row
  //      for each request in rb
  protected void handleRequestBatch(final Object[] rb) throws ClientException, ClientFatalException {
    if (DEBUG) {
      System.out.printf("processing batch of size %d\n", rb.length);
    }

    if (rb.length < 1) { 
      return; 
    }

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
      Object r = rb[i];

      //NOTE: the "assignments" array does not hold vehicle IDs which can be 
      //      used directly. It contains the index of that vehicle inside
      //      the "vehicles" array. Therefore, we first fetch that index,
      //      check if it is -1 (which means that the request should not be assigned to any vehicle)
      //      and if not, proceed to fetch the actual vehicle ID and insert it into the route
      int v_index = assignments[i];
      if (v_index == -1) {
        //add the request back to the queue so that we can try to insert it in the next epoch
        if (DEBUG) {
          System.out.printf("Adding request %d to queue\n",i);
        }
        //TODO: this is where the reactive repositioning should probably come in too
      }
      else {
        int v_id = vehicles[v_index];
        if (DEBUG) {
          System.out.printf("Adding request %d to vehicle with ID %d\n",i,v_id);
        }
        addToRoute(r,v_id);
      }
    }
    if (DEBUG) {
      System.out.printf("---------finished processing batch---------\n");
    }


    
  }

  //performs the Context Mapping as described in Simonetto 2019
  protected Integer[][] contextMappingModule(final Object[] rb) {
    
    Integer [][] contextMapping = new Integer[rb.length][2*MAXN];

    //get the list of available vehicles
    //1. filter out vehicles which are full

    //2. distinguish between vehicles which are idle (i.e. current route is empty)
    //   and those which aren't (i.e. already have scheduled pickups and/or dropoffs)
    //   (probably a good idea to splti them up into two arrays)
    
    //3. for each request:
      //a. calculate distance between each vehicle and the request pickup point

      //b. from the list of idle vehicles, pick the closest "maxn" ones

      //c. from the list of non-idle vehicles, pick "maxn" ones at random

      //d. combine the two arrays to get a single array which represents all candidtate vehicles for the request
    
    //6. return the 2D array of candidate requests
    
    //for now, we will just fill in the context mapping block with random values
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
    return contextMapping;
  }

  //takes the list of requests and output of the context mapping module to
  //form the cost matrix by asking the vehicles for the insertion cost
  protected double [][] getCostMatrix(final Object[] rb, final int [] vehicles, final Integer[][] contextMapping ) {
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
          weight_matrix[i][j]=1000.0;
        }
      }
    }

    //return padCostMatrix(weight_matrix);
    return weight_matrix;
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
  protected double getInsertionCost(final Object r, final int sid){
    //IDEA: if we want to have different cost calculation techniques
    //within the same fleet, we might want to store a map/dictionary
    //with the function to use for each vehicle (or range of vehicles)   

    //for now we just return a random integer to represent the insertion cost
    Random rand = new Random();
    return 1 + (100 - 1) * rand.nextDouble();
  }

  //used to insert a request into a vehicle's route
  protected void addToRoute(final Object r, final int sid){
    //TODO: we need some way to cache the insertion point when we are calculating the insertion cost

    return;    
  }


}
