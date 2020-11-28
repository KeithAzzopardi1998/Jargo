//package com.github.jargors.batchprocessing;
import com.github.jargors.sim.*;
import java.util.Random;

public class Exhaustive extends MLridesharing {
  
  //takes a request and vehicle id and returns the insertion cost
  protected double getInsertionCost_random(final int[] r, final int sid){
    //IDEA: if we want to have different cost calculation techniques
    //within the same fleet, we might want to store a map/dictionary
    //with the function to use for each vehicle (or range of vehicles)   

    //for now we just return a random integer to represent the insertion cost
    Random rand = new Random();
    return 1 + (100 - 1) * rand.nextDouble();
  }

  protected double getInsertionCost(final int[] r, final int sid) {
    final int rid = r[0];
    final int rq  = r[1];
    final int ro  = r[4];
    final int rd  = r[5];

    if (DEBUG) {
      System.out.printf("calculating insertion cost for vehicle ID %d and request ID %d\n",sid,rid);
    }

    final int now = this.communicator.retrieveClock();

    int[] brem = this.communicator.queryServerScheduleRemaining(sid, now);
    if (DEBUG) {
      System.out.printf("got brem=\n");
      for (int __i = 0; __i < (brem.length - 3); __i += 4) {
        System.out.printf("  { %d, %d, %d, %d }\n",
            brem[__i], brem[__i+1], brem[__i+2], brem[__i+3]);
      }
    }

    final int[] wact = this.communicator.queryServerRouteActive(sid);
    if (DEBUG) {
      System.out.printf("got wact=\n");
      for (int __i = 0; __i < (wact.length - 1); __i += 2) {
        System.out.printf("  { %d, %d }\n",
            wact[__i], wact[(__i + 1)]);
      }
    }
  }
}
