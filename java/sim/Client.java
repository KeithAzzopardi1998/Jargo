package com.github.jargors.sim;
import com.github.jargors.sim.Communicator;
import com.github.jargors.sim.Tools;
import com.github.jargors.sim.ClientException;
import com.github.jargors.sim.ClientFatalException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.HashMap;
import java.io.FileNotFoundException;
import java.sql.SQLException;
import java.lang.Math;
import java.nio.file.Paths;
import org.jetbrains.bio.npy.NpyFile;
import org.jetbrains.bio.npy.NpyArray;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.*;
import java.nio.charset.StandardCharsets;


public abstract class Client {
  //by default, we use the Jargo request processing method of loading new customers from the queue and inserting them 
  //one by one. However, inheriting classes may set this flag to TRUE to overwrite this functiinality
  //and get the list of requests for this current time step as a batch instead
  protected boolean batch_processing=false;
  protected ConcurrentLinkedQueue<int[]> queue = new ConcurrentLinkedQueue<int[]>();
  protected Communicator communicator;
  protected Tools tools = new Tools();
  protected final boolean DEBUG =
      "true".equals(System.getProperty("jargors.client.debug"));
  protected final boolean DEMAND_MODEL_ENABLED =
      "true".equals(System.getProperty("jargors.client.dm_enable"));
  //map containing the ID of each server and the time at which it was at the last vertex 
  protected ConcurrentHashMap<Integer, Integer> lut = new ConcurrentHashMap<Integer, Integer>();
  //map containing the ID of each server and the last visited vertex
  protected ConcurrentHashMap<Integer, Integer> luv = new ConcurrentHashMap<Integer, Integer>();
  protected long dur_handle_request = 0;
  //a map listing each jargo node and the corresponding grid space to which it is mapped in
  //the demand prediction model
  protected ConcurrentHashMap<Integer, Integer> jargo_to_dm = new ConcurrentHashMap<Integer, Integer>();
  //height and width of the demand model grid
  //TODO: store these in the mapping file?
  protected final int dm_height = 20;
  protected final int dm_width = 5;
  //the current predictions (explanation on how to use in importFutureRequests)
  /*
  * "dm_predictions_raw" holds the predictions for the current period.
  * Even though this array represents a 3D volume with the predictions, we choose to work with the 1D
  * array directly, for computational efficiency reasons (and the npy library works with ID arrays only)
  * To read the predicted demand from region with ID "dm_origin" to region with ID "dm_destination", use:
  *
  *   demand_od = dm_predictions_raw[ (dm_destination * (dm_height*dm_width)) + dm_origin]
  *
  * NOTE: dm_origin and dm_destination are IDs corresponding to demand model regions, not Jargo nodes
  */
  protected int[] dm_predictions_raw = new int[(this.dm_height*dm_width) * this.dm_height * this.dm_width];
  //old code using a 3D array
  //protected int[][][] dm_predictions_raw = new int[this.dm_height*dm_width][this.dm_height][this.dm_width];
  
  //TODO: separate arrays for the probability distributions, and maybe actual requests?
  public Client() {
    if (DEBUG) {
      System.out.printf("create Client\n");
    }

    if (DEMAND_MODEL_ENABLED) {
      this.jargo_to_dm = this.loadNodeMapping("./demand_model_data/mapping_nodes/node_grid_map_5x20.csv");
      if (DEBUG) {
        System.out.printf("loaded Jargo-to-DemandModel node map\n");
      }
    }
  }
  public boolean isDemandModelEnabled(){
           return DEMAND_MODEL_ENABLED;
         }
  public void forwardRefCacheEdges(final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, int[]>> lu_edges) {
           this.tools.setRefCacheEdges(lu_edges);
         }
  public void forwardRefCacheUsers(final ConcurrentHashMap<Integer, int[]> lu_users) {
           this.tools.setRefCacheUsers(lu_users);
         }
  public void forwardRefCacheVertices(final ConcurrentHashMap<Integer, int[]> lu_vertices) {
           this.tools.setRefCacheVertices(lu_vertices);
         }
  public void setRefCommunicator(final Communicator communicator) {
           this.communicator = communicator;
         }
  public void gtreeLoad(String p) throws FileNotFoundException {
           this.tools.GTGtreeLoad(p);
         }
  public void gtreeClose() {
           this.tools.GTGtreeClose();
         }
  public void addRequest(final int[] r) {
           this.queue.add(r);
         }
  public void collectServerLocations(final int[] src) throws ClientException, ClientFatalException {
            //loop through the servers
            for (int i = 0; i < (src.length - 2); i += 3) {
             this.handleServerLocation(new int[] {
               src[i],//server "s" id
               src[(i + 1)],//time of the last location of "s"
               src[(i + 2)]//vertex of the last location of "s"
             });
           }
         }
  public int dropRequests(final int deadline) {
           final int temp = this.queue.size();
           this.queue.removeIf((r) -> { return r[2] < deadline; });
           return Math.max(0, temp - this.queue.size());
         }
  public Map<Integer, Integer> filterByProximity(
             Map<Integer, Integer> candidates, int threshold, int rid)
             throws ClientException {
           Map<Integer, Integer> results = new HashMap<Integer, Integer>();
           try {
             final int ro = this.communicator.queryUser(rid)[4];
             for (final int sid : candidates.keySet()) {
               final int val = this.tools.computeHaversine(this.luv.get(sid), ro);
               if (0 < val && val <= threshold)
                 results.put(sid, val);
             }
           } catch (Exception e) {
             throw new ClientException(e);
           }
           return results;
         }
  public Map<Integer, Integer> filterByScheduleLength(
             Map<Integer, Integer> candidates, int threshold)
             throws SQLException {
           return filterByScheduleLength(candidates, threshold,
                    this.communicator.retrieveClock());
         }
  public Map<Integer, Integer> filterByScheduleLength(
             Map<Integer, Integer> candidates, int threshold, int time)
             throws SQLException {
           Map<Integer, Integer> results = new HashMap<Integer, Integer>();
           for (final int sid : candidates.keySet()) {
             final int val = this.communicator.queryServerScheduleRemaining(sid, time).length / 4;
             if (val <= threshold) {
               results.put(sid, val);
             }
           }
           return results;
         }
  public long getHandleRequestDur() {
           return this.dur_handle_request;
         }
  public int getQueueSize() {
           return this.queue.size();
         }
  public void init() { }
  public void notifyNew() throws ClientException, ClientFatalException {
            if (batch_processing==true) {
              if (!this.queue.isEmpty()){
                int[][] rb = this.queue.toArray(new int[this.queue.size()][7]);
                if (DEBUG) {
                  int now = this.communicator.retrieveClock();
                  System.out.printf("[t=%d] ---> there are %d requests in the queue\n",now,this.queue.size());
                  //System.out.println(Arrays.deepToString(rb).replace("], ", "]\n").replace("[[", "[").replace("]]", "]"));
                }
                long A0 = System.currentTimeMillis();
                this.handleRequestBatch(rb);
                this.dur_handle_request = System.currentTimeMillis() - A0;
                if (DEBUG) {
                  System.out.printf("----processed request batch in %d ms ... %d requests left in the queue----\n",this.dur_handle_request,this.queue.size());
                }
              }
            } else {
              while (!this.queue.isEmpty()) {
                long A0 = System.currentTimeMillis();
                this.handleRequest(this.queue.remove());
                this.dur_handle_request = System.currentTimeMillis() - A0;
                if (DEBUG) {
                  System.out.printf("handleRequest(1), arg1=[#]\n");
                }
              }
            }
         }
  public int[] routeMinDistMinDur(int sid, int[] bnew, boolean strict) throws ClientException {
           int[] wnew = null;
           boolean ok = true;
           try {
             final int now = this.communicator.retrieveClock();
             int[] wact = this.communicator.queryServerRouteActive(sid);
             int[] wbeg = (wact[3] == 0
                 ? new int[] { now    , wact[1] }
                 : new int[] { wact[2], wact[3] });
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
               // Populate times in the provided schedule
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

             // Check time-windows
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
           } catch (Exception e) {
             throw new ClientException(e);
           }
           return (ok || !strict ? wnew : null);
         }
  public int[] scheduleMinDistInsertion(int sid, int rid) throws ClientException {
           int[] bmin = null;
           try {
             final int now = this.communicator.retrieveClock();
             final int[] r = this.communicator.queryUser(rid);
             final int rq  = r[1];
             final int ro  = r[4];
             final int rd  = r[5];

             int cmin = Integer.MAX_VALUE;

             int[] brem = this.communicator.queryServerScheduleRemaining(sid, now);

             final int[] wact = this.communicator.queryServerRouteActive(sid);

             int[] wbeg = (wact[3] == 0
                 ? new int[] { now    , wact[1] }
                 : new int[] { wact[2], wact[3] });

             // if next events occurs at next waypoint and is not server's own
             // destination, then delete these events from schedule (limitation #4).
             if (brem[2] != sid && brem[0] == wact[2]) {
               while (brem[0] == wact[2]) {
                 brem = Arrays.copyOfRange(brem, 4, brem.length);
               }
             }

             int imax = (brem.length/4);
             int jmax = imax;
             int cost = brem[(brem.length - 4)];

             final int[] bold = brem;

             // Try all insertion positions
             for (int i = 0; i < imax; i++) {
               int tbeg = (i == 0 ? now : brem[4*(i - 1)]);

               for (int j = i; j < jmax; j++) {
                 int tend = bold[4*j];

                 boolean ok = (this.communicator.queryServerCapacityViolations(sid, rq, tbeg, tend)[0] == 0);

                 if (ok) {
                   brem = bold.clone();  // reset to original
                   int[] bnew = new int[] { };

                   int[] stop = new int[] { 0, ro, 0, rid };
                   int ipos = i;

                   // Insert
                   bnew = new int[(brem.length + 4)];
                   System.arraycopy(stop, 0, bnew, 4*ipos, 4);
                   System.arraycopy(brem, 0, bnew, 0, 4*ipos);
                   System.arraycopy(brem, 4*ipos, bnew, 4*(ipos + 1), brem.length - 4*ipos);

                   brem = bnew;

                   stop[1] = rd;
                   ipos = (j + 1);

                   // Insert
                   bnew = new int[(brem.length + 4)];
                   System.arraycopy(stop, 0, bnew, 4*ipos, 4);
                   System.arraycopy(brem, 0, bnew, 0, 4*ipos);
                   System.arraycopy(brem, 4*ipos, bnew, 4*(ipos + 1), brem.length - 4*ipos);

                   int cdel = bnew[(bnew.length - 4)] - cost;
                   if (cdel < cmin) {
                     bmin = bnew;
                     cmin = cdel;
                   }
                 }
               }
             }
           } catch (Exception e) {
             throw new ClientException(e);
           }
           return bmin;
         }
  //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ Functions related to prediction model ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  public ConcurrentHashMap<Integer,Integer> loadNodeMapping(String filepath) {
    ConcurrentHashMap<Integer, Integer> node_map =  new ConcurrentHashMap<Integer, Integer>();
    boolean header = true;//the first line is a header and we don't want to read it
    BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(filepath));
			String line = reader.readLine().trim();
			while (line != null) {
				if (header==false) {
          String[] values = line.split(",");
          //each line contains two values:
          //0 -> Jargo node ID
          //1 -> corresponding Demand Model region ID
          node_map.put(Integer.parseInt(values[0]), Integer.parseInt(values[1]));
        } else {
          //the header is only 1 line, so we invert
          //the bool once we have found it once
          header = false;
        }
				line = reader.readLine();
			}
			reader.close();
		} catch (IOException e) {
			System.out.printf("Could not read mapping file:\n");
      e.printStackTrace();
      System.exit(1);
		}

    return node_map;
  }
  public void updatePredictions() throws ClientException, ClientFatalException {

      try {
        final int now = this.communicator.retrieveClock();
        if (DEBUG) {
          System.out.printf("updatePredictions: called at time %d\n",now);
        }
        //1. updating the numpy files with the requests from previous intervals
        //exportPastRequests(5, 30*60,now);
        exportPastRequests(5, 1*60,now);
        if (DEBUG) {
          System.out.printf("updatePredictions: finished exportPastRequests\n",now);
        }

        //2. calling the python script to predict the next interval
        //IMP: wait for the script to finish before reading the predictions
        runDemandModel();
        if (DEBUG) {
          System.out.printf("updatePredictions: finished runDemandModel\n",now);
        }

        //3. reading the predictions
        importFutureRequests();
        if (DEBUG) {
          System.out.printf("updatePredictions: finished importFutureRequests\n",now);
        }

      } catch (Exception e) {
        System.out.printf("Error occurred when updating predictions:\n");
        e.printStackTrace();
        //TODO throw a ClientException?
      }

  }
  //used to export the past "num_intervals" intervals of
  //length "interval_length" seconds to a .npy file, by calling
  //exportPastRequestInterval on each interval
  //"time_end" refers to the latest time we want to consider
  //(i.e. from where to start working backwards in time)
  public void exportPastRequests(final int num_intervals, final int interval_length, final int time_end) throws SQLException{
      if (DEBUG) {
        System.out.printf("exportPastRequests: num_intervals=%d, interval_length=%d, time_end=%d\n",num_intervals,interval_length,time_end);
      }
      for (int i = 0; i < num_intervals; i++) {
        int interval_end = time_end - (i * interval_length);
        int interval_start = interval_end - interval_length;

        if (DEBUG) {
          System.out.printf("exportPastRequests: handling interval between t=%d and t=%d\n",interval_start,interval_end);
        }
        //we need to check the time so that we don't try to load
        //requests from before the simulation started
        if (interval_start > 0) { //ensuring that we don't try to query outside the simulation
          String interval_filename= String.format("./demand_model_data/input_intervals/interval_%d.npy", (i+1));
          this.exportPastRequestInterval(interval_start, interval_end, interval_filename);
        }
        else {
          if (DEBUG) {
            System.out.printf("exportPastRequests: interval skipped (out of time range)\n",interval_start,interval_end);
          }
        }
        if (DEBUG) {
          System.out.printf("exportPastRequests: finished handling interval\n",interval_start,interval_end);
        }
      }
  }
  public void exportPastRequestInterval(final int t_start, final int t_end, final String filepath) throws SQLException {
      if (DEBUG) {
        System.out.printf("exportPastRequestInterval: t_start=%d, t_end=%d, filepath=%s\n",t_start,t_end,filepath);
      }
      //the OD matrix which will be exported to the npy file
      //(initialized to 0s by default, so we just increment later on)
      //we use the same logic as in dm_predictions_raw to read/write
      int[] od_matrix = new int[this.dm_predictions_raw.length];

      //query requests between t_start and t_end
      //returns the ID, origin node and destination node for each request in the interval [t_start, t_end)
      //note that the origin and destination nodes correspond to Jargo nodes,
      //and need to be mapped to demand model region
      try {
        int[] r_jargo = this.communicator.queryRequestsInInterval(t_start,t_end);
        if (DEBUG) {
          System.out.printf("exportPastRequestInterval: got request array of length %d\n",r_jargo.length);
        }

        for(int i = 0; i < (r_jargo.length-2); i+=3 )//loop through the requests
        {
          //convert from jargo node index to demand model region index
          Integer o_dm = jargo_to_dm.get(r_jargo[i+1]);
          Integer d_dm = jargo_to_dm.get(r_jargo[i+2]);
          if (DEBUG) {
            System.out.printf("exportPastRequestInterval: finished mapping jargo nodes to demand model regions\n");
          }

          if ((o_dm==null) || (d_dm==null)) {
            if (DEBUG) {
              System.out.printf("exportPastRequestInterval -> could not map request with ID %d to demand model\n",r_jargo[i]);
            }
            continue;
          }
          else {
            dm_predictions_raw[ (d_dm * (this.dm_height*this.dm_width)) + o_dm] += 1;


          }
        }
        if (DEBUG) {
          System.out.printf("exportPastRequestInterval: finished building OD array\n");
        }

      } catch (SQLException e) {
        System.err.printf("Error occurred when trying to query requests in interval [%d,%d)\n",
                            t_start,t_end);
        e.printStackTrace();
        throw e;
      }

  }
  // reads a .npy file and returns a (flattened) Numpy array
  public void importFutureRequests() {
      //1. reading the file with the raw predictions
      String raw_filename = "./demand_model_data/predicted_interval/raw.npy";
      if (DEBUG) {
        System.out.printf("importFutureRequests: going to import raw predictions from %s\n",raw_filename);
      }
      NpyArray pred_raw_npy = NpyFile.read(Paths.get(raw_filename),Integer.MAX_VALUE);
      dm_predictions_raw = pred_raw_npy.asIntArray();
      if (DEBUG) {
        System.out.printf("importFutureRequests: loaded raw prediction array of length %d\n",dm_predictions_raw.length);
      }
      //TODO: probability distributions, and sampled requests?
  }
  // runs the demand prediction model script
  public void runDemandModel() throws IOException, InterruptedException{
      try{
        String command = "/home/keith/Dissertation/github/liu_2019/predict_npy.py"
                        + " --in1 ./demand_model_data/input_intervals/interval_1.npy"
                        + " --in2 ./demand_model_data/input_intervals/interval_2.npy"
                        + " --in3 ./demand_model_data/input_intervals/interval_3.npy"
                        + " --in4 ./demand_model_data/input_intervals/interval_4.npy"
                        + " --in5 ./demand_model_data/input_intervals/interval_5.npy"
                        + " --model_file ./demand_model_data/model_files/odonly_20x5_cont.h5"
                        + " --out_raw ./demand_model_data/predicted_interval/raw.npy";
        if (DEBUG) {
          System.out.printf("runDemandModel: going to run script\n");
        }
        Process process = Runtime.getRuntime().exec(command);
        process.waitFor();
        if (DEBUG) {
          System.out.printf("runDemandModel: script finished executing\n");
        }
        if (DEBUG) {
          InputStream stdout = process.getErrorStream();
          BufferedReader reader_out = new BufferedReader(new InputStreamReader(stdout,StandardCharsets.UTF_8));
          InputStream stderr = process.getErrorStream();
          BufferedReader reader_err = new BufferedReader(new InputStreamReader(stderr,StandardCharsets.UTF_8));
          String line;
          System.out.printf("runDemandModel: printing standard output:\n");
          while((line = reader_out.readLine()) != null){
            System.out.println("stdout: "+ line);
          }
          System.out.printf("runDemandModel: printing standard error:\n");
          while((line = reader_err.readLine()) != null){
              System.out.println("stderr: "+ line);
          }
        }
      } catch (Exception e) {
        System.out.println("Exception raised when running demand model" + e.toString());
        throw e;
      }
  }
  //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  protected void end() { }
  protected void handleRequest(final int[] r) throws ClientException, ClientFatalException { }
  protected void handleRequestBatch(final int[][] rb) throws ClientException, ClientFatalException { }
  protected void handleServerLocation(final int[] loc) throws ClientException, ClientFatalException {
              this.lut.put(loc[0], loc[1]);//server ID and time
              this.luv.put(loc[0], loc[2]);//server ID and location
            }
}
