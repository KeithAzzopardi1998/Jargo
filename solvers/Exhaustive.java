//package com.github.jargors.batchprocessing;
import com.github.jargors.sim.*;
import java.util.Random;

public class Exhaustive extends MLridesharing {
  
  //takes a request and vehicle id and returns the insertion cost
  protected double getInsertionCost(final int[] r, final int sid){
    //IDEA: if we want to have different cost calculation techniques
    //within the same fleet, we might want to store a map/dictionary
    //with the function to use for each vehicle (or range of vehicles)   

    //for now we just return a random integer to represent the insertion cost
    Random rand = new Random();
    return 1 + (100 - 1) * rand.nextDouble();
  }
}
