//package com.github.jargors.batchprocessing;
import com.github.jargors.sim.*;
import java.util.Random;
import java.util.Arrays;

public class Baseline extends MLridesharing {
  
  private final boolean DEBUG =
    "true".equals(System.getProperty("jargors.costcalculation.debug"));
  
  //takes a request and vehicle id and returns the insertion cost
  protected double getInsertionCost_random(final int[] r, final int sid){
    //IDEA: if we want to have different cost calculation techniques
    //within the same fleet, we might want to store a map/dictionary
    //with the function to use for each vehicle (or range of vehicles)   

    //for now we just return a random integer to represent the insertion cost
    Random rand = new Random();
    return 1 + (100 - 1) * rand.nextDouble();
  }

  protected double getInsertionCost(final int[] r, final int sid) throws ClientException {
    final int rid = r[0];//id
    final int rq  = r[1];//number of passengers
    final int re  = r[2];//earliest pickup time
    final int rl  = r[3];//latest dropoff time
    final int ro  = r[4];//origin
    final int rd  = r[5];//destination
    final int rb  = r[6];//base cost

    if (DEBUG) {
      System.out.printf("calculating insertion cost for vehicle ID %d and request ID %d\n",sid,rid);
    }

    //dummy vehicle, we don't want it being chosen so we set the cost to an arbitrarily large number
    if (sid == -1) {
      return this.COST_INFEASIBLE;
    }

    try{
      //fetch the server capacity
      final int capacity = this.communicator.queryServerCapacity(sid);
      if (DEBUG) {
        System.out.printf("server capacity is %d\n",capacity);
      }      

      // Remember best (minimum cost) schedule, route, cost
      int[] wmin = null;
      int[] bmin = null;
      int cmin = Integer.MAX_VALUE;


      final int now = this.communicator.retrieveClock();

      if (DEBUG) {
        System.out.printf("fetching routes and schedule for vehicle %d at time %d\n",sid,now);
      }

      //remaining schedule (i.e. pickups and dropoffs)
      int[] brem = this.communicator.queryServerScheduleRemaining(sid, now);
      /*
      if (DEBUG) {
        System.out.printf("got brem=\n");
        for (int __i = 0; __i < (brem.length - 3); __i += 4) {
          System.out.printf("  { %d, %d, %d, %d }\n",
              brem[__i], brem[__i+1], brem[__i+2], brem[__i+3]);
        }
      }
      */

      //the vehicle's schedule of pickups/dropoffs cannot exceed twice its capacity
      //adding a request to the route always adds 2 entries in the schedule, so
      //current_length + 2 <= 2 * capacity
      //keep in mind that each entry in the schedule entails 4 array entries
      if ((brem.length / 4) > ((2 * capacity) - 2)){
        if (DEBUG) {
          System.out.println("~~~~~~~~~~insertion would violate route length constraint~~~~~~~~~~");
        }
        return this.COST_INFEASIBLE;
      }


      //active route (i.e. actual list of nodes left to traverse)
      final int[] wact = this.communicator.queryServerRouteActive(sid);
      /*
      if (DEBUG) {
        System.out.printf("got wact=\n");
        for (int __i = 0; __i < (wact.length - 1); __i += 2) {
          System.out.printf("  { %d, %d }\n",
              wact[__i], wact[(__i + 1)]);
        }
      }
      */
      
      //if wact[3] is 0, it means that the vehicle is idle (because 0 is the index
      //of the dummy vertex), so we start the route ("wbeg" stands for "beginning waypoint")
      //now, and at the vehicle's current location. Else, we start the route at the vehicle's current
      //destination
      int[] wbeg = (wact[3] == 0
          ? new int[] { now    , wact[1] }
          : new int[] { wact[2], wact[3] });
      //if (DEBUG) {
      //  System.out.printf("set wbeg={ %d, %d }\n", wbeg[0], wbeg[1]);
      //}

      // if next events occurs at next waypoint and is not server's own
      // destination, then delete these events from schedule (limitation #4).
      if (brem[2] != sid && brem[0] == wact[2]) {
        if (DEBUG) {
          System.out.printf("detected limitation #4\n");
        }
        while (brem[0] == wact[2]) {
          brem = Arrays.copyOfRange(brem, 4, brem.length);
          if (DEBUG) {
            System.out.printf("remove event\n");
          }
          /*
          if (DEBUG) {
            System.out.printf("got brem=\n");
            for (int __i = 0; __i < (brem.length - 3); __i += 4) {
              System.out.printf("  { %d, %d, %d, %d }\n",
                  brem[__i], brem[__i+1], brem[__i+2], brem[__i+3]);
            }
          }
          */
        }
      } 

      //calculating the direct route duration so that we can later
      //use it to check the maximum delay constraint
      int[] direct_route = this.tools.computeRoute(ro, rd, re);
      int direct_route_dur = direct_route[direct_route.length-2] - direct_route[0];

      int imax = (brem.length/4);
      int jmax = imax;
      int cost = brem[(brem.length - 4)];
      if (DEBUG) {
        System.out.printf("set imax=%d, jmax=%d, cost=%d\n", imax, jmax, cost);
      }

      //"bold" refers to the old schedule, i.e. the schedule
      //of the vehicle before this new request is inserted
      final int[] bold = brem;

      //will be set to true if at least one set of feasible insertion points is found
      boolean feasible =false;



      //i is used to check where the pickup of this request should be inserted,
      //and j is used for the dropoff point
      for (int i = 0; i < imax; i++) {
        //time at which the current pickup/dropoff point is going to be visited
        int tbeg = (i == 0 ? now : brem[4*(i - 1)]);

        for (int j = i; j < jmax; j++) {
          //time at which the next pickup/dropoff point is going to be visited
          int tend = bold[4*j];

          if (DEBUG) {
            System.out.printf("set i=%d, j=%d\n", i, j);
          }
          //if (DEBUG) {
          //  System.out.printf("set tbeg=%d, tend=%d\n", tbeg, tend);
          //}
          
          //used to indicate feasibility of the current insertion point(s) being tested
          boolean ok = false;

          if (DEBUG) {
            System.out.printf("check capacity : ");
          }
          ok = (this.communicator.queryServerCapacityViolations(sid, rq, tbeg, tend)[0] == 0);
          if (DEBUG) {
            System.out.printf("set ok=%s\n", (ok ? "true" : "false"));
          }

          //proceed only if we do not violate capacity constraints
          if (!ok) {
            continue;
          }

          brem = bold.clone();  // reset to original
          int[] bnew = new int[] { };
          
          //inserting the pickup into the schedule at i
          int[] stop = new int[] { 0, ro, 0, rid };
          int ipos = i;
          //if (DEBUG) {
          //  System.out.printf("set stop={ %d, %d, %d, %d }\n",
          //      stop[0], stop[1], stop[2], stop[3]);
          //}
          if (DEBUG) {
            System.out.printf("set ipos=%d\n", ipos);
          }
          //the new schedule will have a length of the old one
          //plus one new entry (four array elements)
          bnew = new int[(brem.length + 4)];
          System.arraycopy(stop, 0, bnew, 4*ipos, 4);
          System.arraycopy(brem, 0, bnew, 0, 4*ipos);
          System.arraycopy(brem, 4*ipos, bnew, 4*(ipos + 1), brem.length - 4*ipos);
          //if (DEBUG) {
          //  System.out.printf("got bnew=\n");
          //  for (int __i = 0; __i < (bnew.length - 3); __i += 4) {
          //    System.out.printf("  { %d, %d, %d, %d }\n",
          //        bnew[__i], bnew[__i+1], bnew[__i+2], bnew[__i+3]);
          //  }
          //}

          brem = bnew;

          //repeating a similar procedure to insert the
          //dropoff into the scedule at j
          stop[1] = rd;
          ipos = (j + 1);
          //if (DEBUG) {
          //  System.out.printf("set stop={ %d, %d, %d, %d }\n",
          //      stop[0], stop[1], stop[2], stop[3]);
          //}
          if (DEBUG) {
            System.out.printf("set ipos=%d\n", ipos);
          }
          bnew = new int[(brem.length + 4)];
          System.arraycopy(stop, 0, bnew, 4*ipos, 4);
          System.arraycopy(brem, 0, bnew, 0, 4*ipos);
          System.arraycopy(brem, 4*ipos, bnew, 4*(ipos + 1), brem.length - 4*ipos);
          /*
          if (DEBUG) {
            System.out.printf("got bnew=\n");
            for (int __i = 0; __i < (bnew.length - 3); __i += 4) {
              System.out.printf("  { %d, %d, %d, %d }\n",
                  bnew[__i], bnew[__i+1], bnew[__i+2], bnew[__i+3]);
            }
          }
          */

          //once we have computed the schedule, we go through it
          //and calculate the actual route to follow
          int[] wnew = null;
          {
            final int _p = (bnew.length/4);
            final int[][] _legs = new int[_p][];

            int[] _leg = this.tools.computeRoute(wbeg[1], bnew[1], wbeg[0]);
            int _n = _leg.length;
            int _t = _leg[(_n - 2)];

            _legs[0] = _leg;
            for (int _i = 1; _i < _p; _i++) {
              // Extract vertices
              final int _u = bnew[(4*_i - 3)];
              final int _v = bnew[(4*_i + 1)];
              // Compute path and store into _legs
              _leg = this.tools.computeRoute(_u, _v, _t);
              _legs[_i] = _leg;
              // Update _n and _t
              _n += (_leg.length - 2);
              _t = _leg[_leg.length - 2];
            }
            wnew = new int[_n];
            int _k = 0;
            for (int _i = 0; _i < _legs.length; _i++) {
              final int _rend = (_legs[_i].length - (_i == (_legs.length - 1) ? 0 : 2));
              for (int _j = 0; _j < _rend; _j++) {
                wnew[_k] = _legs[_i][_j];
                _k++;
              }
            }
            //once we have the route, we can figure out the time at which each point
            //in the schedule is visited
            for (int _i = 1; _i < _legs.length; _i++) {
              bnew[(4*_i - 4)] = _legs[_i][0];
            }
            bnew[(4*_p - 4)] = _t;

            // if next waypoint is vehicle destination,
            // reset route start time to last-visited time
            if (wact[3] == 0) {
              wnew[0] = lut.get(sid);
            }

            /*
            if (DEBUG) {
              System.out.printf("set wnew=\n");
              for (int __i = 0; __i < (wnew.length - 1); __i += 2) {
                System.out.printf("  { %d, %d }\n",
                    wnew[__i], wnew[(__i + 1)]);
              }
            }
            */

            //once the route is calculated, we check the time windows
            //and abandon this configuration if one of them is 
            //violated
            if (DEBUG) {
              System.out.printf("check time window : ");
            }
            for (int _i = 0; _i < (bnew.length - 3); _i += 4) {
              int _rid = bnew[(_i + 3)];
              int _rt  = bnew[(_i)];
              if (_rid != 0) {//rid is only 0 at the end point of the route
                int[] _u = this.communicator.queryUser(_rid);
                int _ue = _u[2];
                int _ul = _u[3];
                //if the proposed pickup time is before the earliest
                //or after the latest dropoff time, consider the route invalid
                if (_rt < _ue || _rt > _ul) {
                  ok = false;
                  break;
                }
              }
            }
            if (DEBUG) {
              System.out.printf("set ok=%s\n", (ok ? "true" : "false"));
            }
          }

          //proceed only if we do not violate time window constraints
          if (!ok) {
            continue;
          }

          //since we have the route, we can check the pickup/delay constraints
          //recall that in bnew, the "time column" is column 0,
          //so we only really need to calculate the 
          int pickup_time = bnew[4*i];
          int dropoff_time = bnew[4*(j+1)];
          if (DEBUG) {
            System.out.printf("got bnew=\n");
            for (int __i = 0; __i < (bnew.length - 3); __i += 4) {
              System.out.printf("  { %d, %d, %d, %d }\n",
                  bnew[__i], bnew[__i+1], bnew[__i+2], bnew[__i+3]);
            }
            System.out.printf("request ID %d : pickup at %d, dropoff at %d\n",rid,pickup_time,dropoff_time);
          }
          //waiting time constraint
          if (pickup_time > re + this.MAX_WT) {
            continue;
          }

          //detour time constraint
          int proposed_trip_dur = dropoff_time - pickup_time;
          if (DEBUG) {
            System.out.printf("durations: proposed %d, direct %d\n",proposed_trip_dur,direct_route_dur);
          }
          if (proposed_trip_dur > direct_route_dur + this.MAX_DT) {
            continue;
          }

          //if we get to this point at least once, there is at least one feasible insertion
          feasible = true;

          int cdel = bnew[(bnew.length - 4)] - cost;
          if (cdel < cmin) {
            bmin = bnew;
            wmin = wnew;
            cmin = cdel;
            //if (DEBUG) {
            //  System.out.printf("got bnew=\n");
            //  for (int __i = 0; __i < (bnew.length - 3); __i += 4) {
            //    System.out.printf("  { %d, %d, %d, %d }\n",
            //        bnew[__i], bnew[__i+1], bnew[__i+2], bnew[__i+3]);
            //  }
            //}
            if (DEBUG) {
              System.out.printf("set cmin=%d\n", cmin);
            }
          }

        }

      }

      if (feasible && cmin>=0) {
        if (DEBUG) {
          System.out.println("~~~~~~~~~~feasible insertion point found~~~~~~~~~~");
        }
        //store the best schedule & route in the cache so that we don't have to recalculate later
        Key<Integer,Integer> k_temp=new Key<Integer,Integer>(rid,sid);
        this.cache_w.put(k_temp,wmin);
        this.cache_b.put(k_temp,bmin);
        //return the lowest insertion cost
        return cmin;

      } else {
        if (DEBUG) {
          System.out.println("~~~~~~~~~~no feasible insertion point found~~~~~~~~~~");
        }
        return this.COST_INFEASIBLE;
      }

    } catch (Exception e) {
      throw new ClientException(e);
    }
  }
}
