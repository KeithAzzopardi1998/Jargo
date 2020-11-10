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

public class MLridesharing extends Client {
  final int MAX_PROXIMITY = 1800;
  public void init() {
    System.out.printf("Set MAX_PROXIMITY=%d\n", MAX_PROXIMITY);
    this.batch_processing=true;
  }
  protected void handleRequestBatch(Object[] rb) throws ClientException, ClientFatalException {
    if (DEBUG) {
      System.out.printf("processing batch of size %d\n", rb.length);
    }

    if (rb.length < 1) { 
      return; 
    }

    //1. call context mapping module
    Integer[][] cm = new Integer[rb.length][20];
    cm = contextMappingModule(rb);
    if (DEBUG) {
      System.out.printf("Context Map: \n");
      for(Integer[] x: cm)
            System.out.println(Arrays.toString(x));  
      System.out.printf("\n-----------------------\n\n");
    }

    //2. ask vehicles for insertion costs
    Integer[][] c = new Integer[rb.length][20];
    c = calculateInsertionCosts(rb,cm);
    if (DEBUG) {
      System.out.printf("Insertion Costs: \n");
      for(Integer[] x: c)
            System.out.println(Arrays.toString(x));  
      System.out.printf("\n-----------------------\n\n");
    }

    //3. call optimization module
    int[] matches = optimizationModule(cm, c);

    //4. update vehicle routes
    
    this.queue.clear();
    //TODO if a request is rejected by the context mapping module, it must be left in the queue
  }

  //performs the Context Mapping describe in Simonetto 2019
  protected Integer[][] contextMappingModule(final Object[] rb) {
    
    Integer [][] contextMapping = new Integer[rb.length][20];

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

  protected Integer[][] calculateInsertionCosts(final Object[] rb, final Integer[][] contextMapping) {
    //for each entry in the context mapping block, we will have an entry in this
    //new array with the corresponding insertion cost
    Integer[][] costs = new Integer[contextMapping.length][contextMapping[0].length];

    //IDEA: if we want to have different cost calculation techniques
    //within the same fleet, we might want to store a map/dictionary
    //with the function to use for each vehicle (or range of vehicles)

    //for now, we will just fill it in with random values between 1 and 100
    for (int row = 0; row < costs.length; row++) {
      for (int col = 0; col < costs[row].length; col++) {
        costs[row][col] = new Random().nextInt(101)  + 1;
      }
    }
    
    return costs;
  }

  protected int[] optimizationModule(final Integer[][] contextMapping, final Integer[][] costs) {
    //we shall store an array with one entry for each request
    //the value corresponds to the vehicle ID
    Integer[] matches = new Integer[contextMapping.length];
 
    int num_customers=contextMapping.length;

    int[] vehicle_list=new int[num_customers*contextMapping[0].length];
    for (int i = 0; i < contextMapping.length; i++) {
        // tiny change 1: proper dimensions
        for (int j = 0; j < contextMapping[0].length; j++) { 
            // tiny change 2: actually store the values
            vehicle_list[(i*contextMapping[0].length) + j] = contextMapping[i][j]; 
        }
    }

    //getting a unique list of vehicles
    int[] vehicles_unique = IntStream.of(vehicle_list).distinct().toArray();
    int num_vehicles=vehicles_unique.length;
    if (DEBUG) {
      System.out.println("Unique list of vehicles\n");
      System.out.println(Arrays.toString(vehicles_unique));
    }

    //building the matrix which will eventually be passed to the matching algorithmbg
    Integer[][] weight_matrix = new Integer[num_customers][num_vehicles];
    //looping through the customers
    for (int i = 0; i < num_customers; i++) {
      //looping through the vehicles
      for (int j = 0; j < num_vehicles; j++) {
        int vehicle_id = vehicles_unique[j];

        //check if this vehicle can serve this customer (from the context mapping module)
        if (Arrays.stream(contextMapping[i]).anyMatch(x -> x == vehicle_id) ) {
          //IDEA: calculate the cost here
        } else {
          //if the vehicle cannot serve this customer, set the weight to infinity 
          weight_matrix[i][j]=Integer.MAX_VALUE;
        }

      }
    }

    return vehicles_unique;

  }
}
