package com.github.jargors.client;
import com.github.jargors.sim.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
public class GreedyInsertion extends Client {
  final int MAX_PROXIMITY = 1800;
  public void init() {
    System.out.printf("Set MAX_PROXIMITY=%d\n", MAX_PROXIMITY);
  }
  protected void handleRequest(int[] r) throws ClientException, ClientFatalException {
              if (DEBUG) {
                System.out.printf("got request={ %d, %d, %d, %d, %d, %d, %d }\n",
                    r[0], r[1], r[2], r[3], r[4], r[5], r[6]);
              }
              try {
                final int rid = r[0];
                final int rq  = r[1];
                final int ro  = r[4];
                final int rd  = r[5];
                
                //store a copy of all vehicles and their last recorded times inside "candidates"
                Map<Integer, Integer> candidates = new HashMap<Integer, Integer>(lut);
                
                //initialize "results" to an empty map
                Map<Integer, Integer> results = new HashMap<Integer, Integer>();
                if (DEBUG) {
                  System.out.printf("got candidates={ #%d }\n", candidates.size());
                }

                //loop through candidatates, and if the vehicle is at most "MAX_PROXIMITY"
                //away from the origin of the request, place it in "results"
                //this means that results is effectively a shortlist of vehicles which are 
                //close to the origin
                for (final int sid : candidates.keySet()) {
                  final int val = this.tools.computeHaversine(luv.get(sid), ro);
                  if (0 < val && val <= MAX_PROXIMITY)
                    results.put(sid, val);
                }

                //update "candidates" so that it only includes the filnext location
                candidates = new HashMap<Integer, Integer>(results);
                if (DEBUG) {
                  System.out.printf("do map/filter: proximity\n");
                }
                if (DEBUG) {
                  System.out.printf("got candidates={ #%d }\n", candidates.size());
                }

                // Remember minimum schedule, route, cost, server
                int[] wmin = null;
                int[] bmin = null;
                int cmin = Integer.MAX_VALUE;
                int smin = 0;

                //loop until we have eliminated all possible servers
                while (!candidates.isEmpty()) {
                  
                  //loop through the candidate vehicles, and store the vehicle
                  //which is closest to the request's origin inside "cand"
                  Entry<Integer, Integer> cand = null;
                  for (final Entry<Integer, Integer> entry : candidates.entrySet()) {
                    if (cand == null || cand.getValue() > entry.getValue()) {
                      cand = entry;
                    }
                  }
                  if (DEBUG) {
                    System.out.printf("got cand={ %d, %d }\n", cand.getKey(), cand.getValue());
                  }

                  final int sid = cand.getKey();
                  final int now = this.communicator.retrieveClock();

                  //schedule -> pickups and dropoffs
                  //(any variable which starts in "b" denotes a schedule)
                  //"brem" is the remaining schedule i.e. yet to be visited
                  //four successive elements represent one entry:
                  //0 -> time at which the pickup/delivery waypoint is to be visited
                  //1 -> request pickup/delivery waypoint
                  //2 -> 0
                  //3 -> request id
                  //except for the final entry, which represents:
                  //0 -> server end point time
                  //1 -> server end point vertex
                  //2 -> server id
                  //3 -> 0                  
                  int[] brem = this.communicator.queryServerScheduleRemaining(sid, now);
                  if (DEBUG) {
                    System.out.printf("got brem=\n");
                    for (int __i = 0; __i < (brem.length - 3); __i += 4) {
                      System.out.printf("  { %d, %d, %d, %d }\n",
                          brem[__i], brem[__i+1], brem[__i+2], brem[__i+3]);
                    }
                  }
                  
                  //route -> actual route traversed in the network
                  //(any variable which starts in "w" denotes a route)
                  //"wact" is the route represented by an array in which each
                  //two successive elements represent one entry, in which the second
                  //is the vertex to visit, and the first is the time at which it will be visited
                  final int[] wact = this.communicator.queryServerRouteActive(sid);
                  if (DEBUG) {
                    System.out.printf("got wact=\n");
                    for (int __i = 0; __i < (wact.length - 1); __i += 2) {
                      System.out.printf("  { %d, %d }\n",
                          wact[__i], wact[(__i + 1)]);
                    }
                  }

                  //it makes sense that wact has at least 4 elements:
                  //0 -> timestamp of the last visited vertex
                  //1 -> last visited vertex (current location?)
                  //2 -> timestamp of when the next location will be visited
                  //3 -> the location to which the vehicle is travelling
                  
                  //if wact[3] is 0, it means that the vehicle is idle (because 0 is the index
                  //of the dummy vertex), so we start the route ("wbeg" stands for "beginning waypoint")
                  //now, and at the vehicle's current location. Else, we start the route at the vehicle's current
                  //destination
                  int[] wbeg = (wact[3] == 0
                      ? new int[] { now    , wact[1] }
                      : new int[] { wact[2], wact[3] });
                  if (DEBUG) {
                    System.out.printf("set wbeg={ %d, %d }\n", wbeg[0], wbeg[1]);
                  }

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
                      if (DEBUG) {
                        System.out.printf("got brem=\n");
                        for (int __i = 0; __i < (brem.length - 3); __i += 4) {
                          System.out.printf("  { %d, %d, %d, %d }\n",
                              brem[__i], brem[__i+1], brem[__i+2], brem[__i+3]);
                        }
                      }
                    }
                  }

                  int imax = (brem.length/4);
                  int jmax = imax;
                  int cost = brem[(brem.length - 4)];
                  if (DEBUG) {
                    System.out.printf("set imax=%d, jmax=%d, cost=%d\n", imax, jmax, cost);
                  }
                  
                  //"bold" refers to the old schedule, i.e. the schedule
                  //of the vehicle before this new request is inserted
                  final int[] bold = brem;

                  // Try all insertion positions
                  if (DEBUG) {
                    System.out.printf("start insertion heuristic\n");
                  }
                  for (int i = 0; i < imax; i++) {
                    //time at which the current pickup/dropoff point is going to be visited
                    int tbeg = (i == 0 ? now : brem[4*(i - 1)]);

                    for (int j = i; j < jmax; j++) {
                      //time at which the next pickup/dropoff point is going to be visited
                      int tend = bold[4*j];

                      if (DEBUG) {
                        System.out.printf("set i=%d, j=%d\n", i, j);
                      }
                      if (DEBUG) {
                        System.out.printf("set tbeg=%d, tend=%d\n", tbeg, tend);
                      }

                      boolean ok = false;

                      if (DEBUG) {
                        System.out.printf("check capacity\n");
                      }
                      ok = (this.communicator.queryServerCapacityViolations(sid, rq, tbeg, tend)[0] == 0);
                      if (DEBUG) {
                        System.out.printf("set ok=%s\n", (ok ? "true" : "false"));
                      }
                      //proceed only if we do not violate capacity constraints
                      if (ok) {
                        brem = bold.clone();  // reset to original
                        int[] bnew = new int[] { };

                        int[] stop = new int[] { 0, ro, 0, rid };
                        int ipos = i;
                        if (DEBUG) {
                          System.out.printf("set stop={ %d, %d, %d, %d }\n",
                              stop[0], stop[1], stop[2], stop[3]);
                        }
                        if (DEBUG) {
                          System.out.printf("set ipos=%d\n", ipos);
                        }
                        //the new schedule will have a length of the old one
                        //plus one new entry (four array elements)
                        bnew = new int[(brem.length + 4)];
                        System.arraycopy(stop, 0, bnew, 4*ipos, 4);
                        System.arraycopy(brem, 0, bnew, 0, 4*ipos);
                        System.arraycopy(brem, 4*ipos, bnew, 4*(ipos + 1), brem.length - 4*ipos);
                        if (DEBUG) {
                          System.out.printf("got bnew=\n");
                          for (int __i = 0; __i < (bnew.length - 3); __i += 4) {
                            System.out.printf("  { %d, %d, %d, %d }\n",
                                bnew[__i], bnew[__i+1], bnew[__i+2], bnew[__i+3]);
                          }
                        }

                        brem = bnew;

                        stop[1] = rd;
                        ipos = (j + 1);
                        if (DEBUG) {
                          System.out.printf("set stop={ %d, %d, %d, %d }\n",
                              stop[0], stop[1], stop[2], stop[3]);
                        }
                        if (DEBUG) {
                          System.out.printf("set ipos=%d\n", ipos);
                        }
                        bnew = new int[(brem.length + 4)];
                        System.arraycopy(stop, 0, bnew, 4*ipos, 4);
                        System.arraycopy(brem, 0, bnew, 0, 4*ipos);
                        System.arraycopy(brem, 4*ipos, bnew, 4*(ipos + 1), brem.length - 4*ipos);
                        if (DEBUG) {
                          System.out.printf("got bnew=\n");
                          for (int __i = 0; __i < (bnew.length - 3); __i += 4) {
                            System.out.printf("  { %d, %d, %d, %d }\n",
                                bnew[__i], bnew[__i+1], bnew[__i+2], bnew[__i+3]);
                          }
                        }

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
                          for (int _i = 1; _i < _legs.length; _i++) {
                            bnew[(4*_i - 4)] = _legs[_i][0];
                          }
                          bnew[(4*_p - 4)] = _t;
                        }

                        // if next waypoint is vehicle destination,
                        // reset route start time to last-visited time
                        if (wact[3] == 0) {
                          wnew[0] = lut.get(sid);
                        }

                        if (DEBUG) {
                          System.out.printf("set wnew=\n");
                          for (int __i = 0; __i < (wnew.length - 1); __i += 2) {
                            System.out.printf("  { %d, %d }\n",
                                wnew[__i], wnew[(__i + 1)]);
                          }
                        }

                        if (DEBUG) {
                          System.out.printf("check time window\n");
                        }
                        for (int _i = 0; _i < (bnew.length - 3); _i += 4) {
                          int _rid = bnew[(_i + 3)];
                          int _rt  = bnew[(_i)];
                          if (_rid != 0) {
                            int[] _u = this.communicator.queryUser(_rid);
                            int _ue = _u[2];
                            int _ul = _u[3];
                            if (_rt < _ue || _rt > _ul) {
                              ok = false;
                              break;
                            }
                          }
                        }
                        if (DEBUG) {
                          System.out.printf("set ok=%s\n", (ok ? "true" : "false"));
                        }

                        if (ok) {
                          int cdel = bnew[(bnew.length - 4)] - cost;
                          if (cdel < cmin) {
                            bmin = bnew;
                            wmin = wnew;
                            cmin = cdel;
                            smin = sid;
                            if (DEBUG) {
                              System.out.printf("got bnew=\n");
                              for (int __i = 0; __i < (bnew.length - 3); __i += 4) {
                                System.out.printf("  { %d, %d, %d, %d }\n",
                                    bnew[__i], bnew[__i+1], bnew[__i+2], bnew[__i+3]);
                              }
                            }
                            if (DEBUG) {
                              System.out.printf("set cmin=%d\n", cmin);
                            }
                          }
                        }

                      }
                    }
                  }
                  if (DEBUG) {
                    System.out.printf("end insertion heuristic\n");
                  }

                  candidates.remove(sid);
                  if (DEBUG) {
                    System.out.printf("remove candidate %d\n", sid);
                  }
                }

                if (DEBUG) {
                  System.out.printf("got candidates={ #%d }\n", candidates.size());
                }

                if (smin != 0) {
                  this.communicator.updateServerService(smin, wmin, bmin,
                      new int[] { rid }, new int[] { });
                }
              } catch (Exception e) {
                throw new ClientException(e);
              }
            }
}
