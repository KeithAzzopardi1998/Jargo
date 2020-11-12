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

    //2. ask vehicles for insertion costs and form the cost matrix
    double[][] c = getCostMatrix(rb,cm);
    if (DEBUG) {
      System.out.printf("Insertion Costs: \n");
      for(double[] x: c)
            System.out.println(Arrays.toString(x));  
      System.out.printf("\n-----------------------\n\n");
    }

    //3. call optimization module
    HungarianAlgorithm optModule = new HungarianAlgorithm(c);
    int[] assignments = optModule.execute();
    System.out.printf("assignments: \n");
    System.out.println(Arrays.toString(assignments));  
    System.out.printf("\n-----------------------\n\n");

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

  //takes the list of requests and output of the context mapping module to
  //form the cost matrix by asking the vehicles for the insertion cost
  protected double [][] getCostMatrix(final Object[] rb, final Integer[][] contextMapping ) {
    int num_customers=rb.length;

    //getting a unique list of vehicles by first flattening out the context map, and then
    //adding to an array of unique items
    int[] vehicle_list=new int[num_customers*contextMapping[0].length];
    for (int i = 0; i < contextMapping.length; i++) {
        // tiny change 1: proper dimensions
        for (int j = 0; j < contextMapping[0].length; j++) { 
            // tiny change 2: actually store the values
            vehicle_list[(i*contextMapping[0].length) + j] = contextMapping[i][j]; 
        }
    }
    int[] vehicles = IntStream.of(vehicle_list).distinct().toArray();
    int num_vehicles=vehicles.length;
    if (DEBUG) {
      System.out.println("Unique list of vehicles\n");
      System.out.println(Arrays.toString(vehicles));
    }

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

  protected void optimizationModule(final Integer[][] costMatrix) {
    //if the cost matrix is sparse, it might be worth looking into the Jonker-Volgenant algorithm
    //(https://javadoc.scijava.org/Fiji/fiji/plugin/trackmate/tracking/sparselap/linker/LAPJV.html) 

    //implementation of the Hungarian algorithm which may also allow for non-square inputs
    //https://github.com/KevinStern/software-and-algorithms/blob/master/src/main/java/blogspot/software_and_algorithms/stern_library/optimization/HungarianAlgorithm.java
  }
}
