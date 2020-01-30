package com.github.jargors.algo;
import com.github.jargors.Client;
import com.github.jargors.exceptions.ClientException;
import com.github.jargors.exceptions.ClientFatalException;
import com.github.jargors.exceptions.VertexNotFoundException;
import com.github.jargors.exceptions.EdgeNotFoundException;
import com.github.jargors.exceptions.UserNotFoundException;
import com.github.jargors.exceptions.GtreeNotLoadedException;
import com.github.jargors.exceptions.GtreeIllegalSourceException;
import com.github.jargors.exceptions.RouteIllegalOverwriteException;
import com.github.jargors.exceptions.TimeWindowException;
import java.sql.SQLException;
public class GreedyInsertion extends Client {
  final int PICKUP_THRESHOLD = 600;  // meters
  final int MAX_SCHEDULE_LENGTH = 8;
  int[] locations = new int[] { };
  int count_rejections = 0;
  protected void handleRequest(int[] r) throws ClientException, ClientFatalException {
              try {
                if (DEBUG) {
                  tools.Print("Extract request { rid="+r[0]+", rq="+r[1]+", ro="+r[4]+", rd="+r[5]+" }");
                }
                int     opt_k       = -1;
                int     opt_c       = Integer.MAX_VALUE;
                int[]   opt_b       = new int[] { };
                int[][] opt_cache_t = new int[][] { };
                int[][] opt_cache_v = new int[][] { };
                if (DEBUG) {
                  tools.Print("Reset opt_k=-1, opt_c=Integer.MAX_VALUE, opt_b={}, opt_cache_t={}, opt_cache_v={}");
                }
                final int   T = communicator.retrieveClock();
                final int[] L = locations.clone();
                final int[] C = tools.filterByHaversine(r[4], L, PICKUP_THRESHOLD);
                if (DEBUG) {
                  tools.Print("Initialize T="+T+"; C.length="+C.length);
                }
                for (int k_cand : C) {
                  final int sid = L[(k_cand + 0)];
                  final int st  = L[(k_cand + 1)];
                  final int sv  = L[(k_cand + 2)];
                  if (DEBUG) {
                    tools.Print("Extract server { sid="+sid+", st="+st+", sv="+sv+" }");
                  }
                  int     s_k       = -1;
                  int     s_c       = Integer.MAX_VALUE;
                  int[]   s_b       = new int[] { };
                  int[][] s_cache_t = new int[][] { };
                  int[][] s_cache_v = new int[][] { };
                  if (DEBUG) {
                    tools.Print("Reset s_k=-1, s_c=Integer.MAX_VALUE, s_b={}, s_cache_t={}, s_cache_v={}");
                  }
                  final int[] y = communicator.queryServerScheduleRemaining(sid, T);
                  final int   n = (y.length/4);
                  final int   z = communicator.queryServerDistanceRemaining(sid, T)[0];
                  if (DEBUG) {
                    tools.Print("Initialize y.length="+y.length+"; n="+n+"; z="+z);
                  }
                  if (n == 1) {
                    if (DEBUG) {
                      tools.Print("Detected n=1");
                    }
                    // Beware! Important note! Jargo cannot handle the case where a customer
                    // appears at the same vertex that a server is idling at! The reason is
                    // because the route would be recorded as
                    //   (t1, v)
                    //   (t2, v)
                    // due to preserving history, (t1, v) cannot be changed; the customer appears
                    // at t2; a new waypoint (t2, v) must be recorded in the route in order to be
                    // referenceable by Table PD, causing the self-referencing edge (v, v)! This
                    // edge violates a Table E constraint.
                    //
                    // As a workaround, we skip the server if it is idling and happens
                    // to be at the request origin.
                    if (sv == r[4]) {
                      continue;
                    }

                    int[] b = new int[12];
                    b[0] = st;
                    b[1] = sv;
                    b[2] = sid;
                    b[4] = r[4];
                    b[5] = r[0];
                    b[7] = r[5];
                    b[8] = r[0];
                    b[10] = y[1];
                    b[11] = sid;
                    if (DEBUG) {
                      tools.Print("Set b[0]="+b[0]);
                      tools.Print("Set b[1]="+b[1]);
                      tools.Print("Set b[2]="+b[2]);
                      tools.Print("Set b[4]="+b[4]);
                      tools.Print("Set b[5]="+b[5]);
                      tools.Print("Set b[7]="+b[7]);
                      tools.Print("Set b[8]="+b[8]);
                      tools.Print("Set b[10]="+b[10]);
                      tools.Print("Set b[11]="+b[11]);
                    }
                    int[][] cache_t = new int[3][];
                    int[][] cache_v = new int[3][];
                    int c = computeCost(b, cache_t, cache_v, s_c, z, r[2]);
                    if (DEBUG) {
                      tools.Print("computeCost returned c="+c);
                    }
                    if (c != -1) {
                      if ((c - z) < s_c) {
                        if (DEBUG) {
                          tools.Print("Detected ("+c+" - "+z+")="+(c - z)+" is less than "+s_c);
                        }
                        s_k = k_cand;
                        s_c = (c - z);
                        s_b = b;
                        s_cache_t = cache_t;
                        s_cache_v = cache_v;
                        if (DEBUG) {
                          tools.Print("Replace incumbent single solution, set s_k="+s_k+"; s_c="+s_c+"; s_b.length="+s_b.length);
                        }
                        if (s_c < 0) {
                          throw new ClientException("Negative detour");
                        }
                      } else {
                        if (DEBUG) {
                          tools.Print("Detected ("+c+" - "+z+")="+(c - z)+" is greater than "+s_c+"; Keep incumbent single solution");
                        }
                      }
                    }
                  }
                  if (n > 1 && n < MAX_SCHEDULE_LENGTH) {
                    if (DEBUG) {
                      tools.Print("Detected n > 1");
                    }
                    // Beware! Important note! Jargo cannot handle the case where a customer
                    // appears at the same vertex that a server was last seen! In other words,
                    // ro cannot equal sv! The reason is that during schedule update, we
                    // delete from CQ starting from st onward before re-inserting the new schedule.
                    // If there are existing labels on st in CQ, then we would be inserting a
                    // second entry on st, causing a constraint violation. If we delete the
                    // existing labels, we would have to first query for them and then re-add them.
                    // This could be a future fix.
                    //
                    // As a workaround, we skip the server if it's last-seen vertex happens
                    // to be at the request origin.
                    if (sv == r[4]) {
                      continue;
                    }

                    for (int i = 0; i < n; i++) {
                      for (int j = i; j < n; j++) {
                        boolean capacity_ok = true;
                        if (DEBUG) {
                          tools.Print("Set i="+i+", j="+j+", capacity_ok=true");
                        }
                        int[] b = new int[3*(n + 3)];
                        for (int p = 0; p < (b.length/3); p++) {
                          if (DEBUG) {
                            tools.Print("Set p="+p);
                          }
                          if (DEBUG) {
                            tools.Print("Check capacity");
                          }
                          if (p >= i && p <= j) {
                            if (DEBUG) {
                              tools.Print("Detected p >= "+i+" && p <= "+j+"; Check capacity");
                            }
                            if (r[1] + communicator.queryServerLoadMax(sid, (p == 0 ? st : y[(4*(p - 1))]))[0] > 0) {
                              if (DEBUG) {
                                tools.Print("Detected capacity violation; Break");
                              }
                              capacity_ok = false;
                              break;
                            }
                          }
                          if (p == (n + 2)) {
                            b[(3*p + 1)] = y[(4*(p - 3) + 1)];
                            b[(3*p + 2)] = y[(4*(p - 3) + 2)];  // server label
                          } else if (p > (j + 2)) {
                            b[(3*p + 1)] = y[(4*(p - 3) + 1)];
                            b[(3*p + 2)] = y[(4*(p - 3) + 3)];
                          } else if (p == (j + 2)) {
                            b[(3*p + 1)] = r[5];
                            b[(3*p + 2)] = r[0];
                          } else if (p > (i + 1)) {
                            b[(3*p + 1)] = y[(4*(p - 2) + 1)];
                            b[(3*p + 2)] = y[(4*(p - 2) + 3)];
                          } else if (p == (i + 1)) {
                            b[(3*p + 1)] = r[4];
                            b[(3*p + 2)] = r[0];
                          } else if (p > 0) {
                            b[(3*p + 0)] = y[(4*(p - 1) + 0)];
                            b[(3*p + 1)] = y[(4*(p - 1) + 1)];
                            b[(3*p + 2)] = y[(4*(p - 1) + 3)];
                            if (DEBUG) {
                              tools.Print("Set b["+(3*p + 0)+"]="+b[(3*p + 0)]);
                            }
                          } else {
                            b[(3*p + 0)] = st;
                            b[(3*p + 1)] = sv;
                            b[(3*p + 2)] = sid;
                            if (DEBUG) {
                              tools.Print("Set b["+(3*p + 0)+"]="+b[(3*p + 0)]);
                            }
                          }
                          if (DEBUG) {
                            tools.Print("Set b["+(3*p + 1)+"]="+b[(3*p + 1)]);
                            tools.Print("Set b["+(3*p + 2)+"]="+b[(3*p + 2)]);
                          }
                        }
                        if (capacity_ok) {
                          int[][] cache_t = new int[(b.length/3 - 1)][];
                          int[][] cache_v = new int[(b.length/3 - 1)][];
                          int c = computeCost(b, cache_t, cache_v, s_c, z, 0);
                          if (DEBUG) {
                            tools.Print("computeCost returned c="+c);
                          }
                          if (c != -1) {
                            if ((c - z) < s_c) {
                              if (DEBUG) {
                                tools.Print("Detected ("+c+" - "+z+")="+(c - z)+" is less than "+s_c);
                              }
                              s_k = k_cand;
                              s_c = (c - z);
                              s_b = b;
                              s_cache_t = cache_t;
                              s_cache_v = cache_v;
                              if (DEBUG) {
                                tools.Print("Replace incumbent single solution, set s_k="+s_k+"; s_c="+s_c+"; s_b.length="+s_b.length);
                              }
                              if (s_c < 0) {
                                throw new ClientException("Negative detour");
                              }
                            } else {
                              if (DEBUG) {
                                tools.Print("Detected ("+c+" - "+z+")="+(c - z)+" is greater than "+s_c+"; Keep incumbent single solution");
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                  if (n >= MAX_SCHEDULE_LENGTH) {
                    if (DEBUG) {
                      tools.Print("Detected n >= MAX_SCHEDULE_LENGTH");
                    }
                  }
                  if (s_c < opt_c) {
                    if (DEBUG) {
                      tools.Print("Detected "+s_c+" < "+opt_c);
                    }
                    opt_k = s_k;
                    opt_c = s_c;
                    opt_b = s_b;
                    opt_cache_t = s_cache_t;
                    opt_cache_v = s_cache_v;
                    if (DEBUG) {
                      tools.Print("Replace incumbent server solution, set opt_k="+opt_k+"; opt_c="+opt_c+"; opt_b.length="+s_b.length);
                    }
                  } else {
                    if (DEBUG) {
                      tools.Print("Detected "+s_c+" > "+opt_c+"; Keep incumbent server solution");
                    }
                  }
                }
                if (opt_k != -1) {
                  int w_len = 1;
                  for (int[] leg : opt_cache_v) {
                    w_len += (leg.length - 1);
                  }
                  w_len *= 2;
                  if (DEBUG) {
                    tools.Print("Construct w from cache");
                    tools.Print("Initialize w.length="+w_len);
                  }
                  int[] opt_w = new int[w_len];
                  opt_w[0] = opt_cache_t[0][0];
                  opt_w[1] = opt_cache_v[0][0];
                  if (DEBUG) {
                    tools.Print("Set opt_w[0]="+opt_w[0]);
                    tools.Print("Set opt_w[1]="+opt_w[1]);
                  }
                  int base = 0;
                  for (int p = 0; p < opt_cache_v.length; p++) {
                    if (p > 0) {
                      base += 2*(opt_cache_v[(p - 1)].length - 1);
                    }
                    for (int q = 1; q < opt_cache_v[p].length; q++) {
                      opt_w[(base + 2*q + 0)] = opt_cache_t[p][q];
                      opt_w[(base + 2*q + 1)] = opt_cache_v[p][q];
                      if (DEBUG) {
                        tools.Print("Set opt_w["+(base + 2*q + 0)+"]="+opt_w[(base + 2*q + 0)]);
                        tools.Print("Set opt_w["+(base + 2*q + 1)+"]="+opt_w[(base + 2*q + 1)]);
                      }
                    }
                  }
                  int sid = L[opt_k];
                  int[] rids = new int[] { r[0] };
                  if (DEBUG) {
                    tools.Print("Submit sid="+sid+"; opt_w.length="+opt_w.length
                      +"; opt_b.length="+opt_b.length+"; opt_c="+opt_c+"; rids.length="+rids.length);
                  }
                  try {
                    // We added the server's current location to b to help us compute cost,
                    // but now we remove the location because it's not really a part of the
                    // schedule.
                    int[] opt_opt_b = new int[(opt_b.length - 3)];
                    for (int i = 3; i < opt_b.length; i++) {
                      opt_opt_b[(i - 3)] = opt_b[i];
                    }
                    communicator.updateServerService(sid, opt_w, opt_opt_b, rids, new int[] { });
                  } catch (RouteIllegalOverwriteException e) {
                    count_rejections++;
                    if (DEBUG) {
                      tools.Print("Submission rejected due to illegal overwrite!");
                    }
                  } catch (TimeWindowException e) {
                    count_rejections++;
                    if (DEBUG) {
                      tools.Print("Submission rejected due to time window violation");
                      tools.Print(e.toString());
                    }
                  }
                } else {
                  if (DEBUG) {
                    tools.Print("No match found");
                  }
                  communicator.forwardReturnRequest(r);
                }
              } catch (SQLException e) {
                throw new ClientException(e);
              } catch (VertexNotFoundException | EdgeNotFoundException | UserNotFoundException e) {
                throw new ClientException(e);
              }
            }
  protected void endCollectServerLocations(int[] src) {
              locations = src.clone();
            }
  protected void end() {
              tools.Print("Count rejections: "+count_rejections);
            }
  private int computeCost(int[] b, int[][] cache_t, int[][] cache_v, int s_c, int z, int d_init)
          throws ClientException, ClientFatalException {
            try {
              if (DEBUG) {
                tools.Print("computeCost called on b.length="+b.length+", s_c="+s_c+", z="+z);
              }
              int c = 0;
              int d = 0;
              int[] leg = new int[] { };
              int[] ddnu = new int[] { };
              if (DEBUG) {
                tools.Print("..set c=0");
              }
              for (int p = 1; p < (b.length/3); p++) {
                if (DEBUG) {
                  tools.Print("..set p="+p);
                }
                leg = tools.computeShortestPath(b[(3*(p - 1) + 1)], b[(3*p + 1)]);
                if (DEBUG) {
                  tools.Print("..call computeShortestPath("+b[(3*(p - 1) + 1)]+", "+b[(3*p + 1)]+")");
                }
                d = b[(3*(p - 1))];
                if (DEBUG) {
                  tools.Print("..set d="+d);
                }
                cache_t[(p - 1)] = new int[leg.length];
                cache_t[(p - 1)][0] = d;
                if (DEBUG) {
                  tools.Print("..set cache_t["+(p - 1)+"]=new int["+leg.length+"]");
                  tools.Print("..set cache_t["+(p - 1)+"][0]="+d);
                }
                if (p == 1) {
                  d += d_init;
                  if (DEBUG) {
                    tools.Print("..add d_init; d="+d);
                  }
                }
                for (int q = 1; q < leg.length; q++) {
                  if (DEBUG) {
                    tools.Print("....set q="+q);
                  }
                  ddnu = communicator.queryEdge(leg[(q - 1)], leg[q]);
                  if (DEBUG) {
                    tools.Print("....call queryEdge("+leg[(q - 1)]+", "+leg[q]+")");
                  }
                  c += ddnu[0];
                  d += tools.computeDuration(ddnu[0], ddnu[1]);
                  if (DEBUG) {
                    tools.Print("....set c="+c);
                    tools.Print("....set d="+d);
                  }
                  cache_t[(p - 1)][q] = d;
                  if (DEBUG) {
                    tools.Print("....set cache_t["+(p - 1)+"]["+q+"]="+d);
                  }
                }
                if ((c - z) > s_c) {
                  if (DEBUG) {
                    tools.Print("Detected cost infeasible ("+(c - z)+" > "+s_c+"); Return");
                  }
                  return -1;
                }
                int[] u = communicator.queryUser(b[(3*p + 2)]);
                if (d < u[2] || d > u[3]) {
                  if (DEBUG) {
                    tools.Print("Detected time infeasible for user "+b[(3*p + 2)]+"; d="+d+"; u[2]="+u[2]+"; u[3]="+u[3]+"; Return");
                  }
                  return -1;
                }
                b[(3*p)] = d;
                if (DEBUG) {
                  tools.Print("..set b["+(3*p)+"]="+d);
                }
                cache_v[(p - 1)] = leg.clone();
                if (DEBUG) {
                  tools.Print("..set cache_v["+(p - 1)+"]=<leg.length="+leg.length+">");
                }
              }
              return c;
            } catch (SQLException e) {
              tools.Print("Something very bad happened");
              tools.PrintSQLException(e);
              throw new ClientFatalException();
            } catch (GtreeNotLoadedException e) {
              tools.Print("Gtree not loaded, can't continue!");
              throw new ClientFatalException();
            } catch (GtreeIllegalSourceException e) {
              throw new ClientException(e);
            } catch (EdgeNotFoundException | UserNotFoundException e) {
              throw new ClientException(e);
            }
          }
}