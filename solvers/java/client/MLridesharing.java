package com.github.jargors.client;
import com.github.jargors.sim.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
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

    //1. call context mapping module

    //2. ask vehicles for insertion costs

    //3. call optimization module

    //4. update vehicle routes
    
    this.queue.clear();
    //TODO if a request is rejected by the context mapping module, it must be left in the queue
  }

  //performs the Context Mapping describe in Simonetto 2019
  protected Object[] contextMappingModule(Object[] rb) {
    
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
    
    return rb;
  }


}
