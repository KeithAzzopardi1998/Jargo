package ridesharing;
import com.github.jargors.sim.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.sql.SQLException;
import java.util.stream.Collectors;

public abstract class DemandPredictionModule {
    boolean DEBUG;
    //a map listing each jargo node and the corresponding grid space to which it is mapped in
    //the demand prediction model
    protected ConcurrentHashMap<Integer, Integer> jargo_to_dm = new ConcurrentHashMap<Integer, Integer>();
    //height and width of the demand model grid
    //TODO: store these in the mapping file?
    protected final int dm_height;
    protected final int dm_width;
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
    protected int[] dm_predictions_raw;

    protected Communicator communicator;

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

    public void exportPastRequestInterval(final int t_start, final int t_end, final String filepath) throws SQLException, IOException {
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

                if ((o_dm==null) || (d_dm==null)) {
                    if (DEBUG) {
                        System.out.printf("exportPastRequestInterval -> could not map request with ID %d to demand model\n",r_jargo[i]);
                    }
                    continue;
                }
                else {
                    od_matrix[ (d_dm * (this.dm_height*this.dm_width)) + o_dm] += 1;
                }
            }

            //writing to the file
            FileWriter file = new FileWriter(filepath);
            BufferedWriter output = new BufferedWriter(file);
            String data = Arrays.toString(od_matrix);
            data = data.substring(1,data.length() - 1);//removing the square brackets
            output.write(data);
            output.close();

            if (DEBUG) {
                System.out.printf("exportPastRequestInterval: finished exporting OD array\n");
            }

        } catch (Exception e) {
            System.err.printf("Error occurred when trying to export requests in interval [%d,%d)\n",
                                t_start,t_end);
            e.printStackTrace();
            throw e;
        }

    }
    //used to export the past "num_intervals" intervals of
    //length "interval_length" seconds to a .npy file, by calling
    //exportPastRequestInterval on each interval
    //"time_end" refers to the latest time we want to consider
    //(i.e. from where to start working backwards in time)
    public void exportPastRequests(final int num_intervals, final int interval_length, final int time_end) throws SQLException, IOException{
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
            String interval_filename= String.format("./demand_model_data/input_intervals/interval_%d.txt", (i+1));
            this.exportPastRequestInterval(interval_start, interval_end, interval_filename);
            if (DEBUG) {
                System.out.printf("exportPastRequests: finished handling interval\n",interval_start,interval_end);
            }
            }
            else {
                if (DEBUG) {
                    System.out.printf("exportPastRequests: interval skipped (out of time range)\n",interval_start,interval_end);
                }
            }
        }
    }    

    public void updatePredictions() throws ClientException, ClientFatalException {
        try {
            long A0 = System.currentTimeMillis();
            final int now = this.communicator.retrieveClock();
            if (DEBUG) {
            System.out.printf("updatePredictions: called at time %d\n",now);
            }
            //1. updating the numpy files with the requests from previous intervals
            exportPastRequests(5, 30*60,now);
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
            if (DEBUG) {
                System.out.printf("updatePredictions: took %d ms in total\n",System.currentTimeMillis() - A0);
            }

        } catch (Exception e) {
            System.out.printf("Error occurred when updating predictions:\n");
            e.printStackTrace();
            //TODO throw a ClientException?
        }
    }    


  // reads a .npy file and returns a (flattened) Numpy array
    public void importFutureRequests() throws IOException {
    //1. reading the file with the raw predictions
    try {
        String raw_filename = "./demand_model_data/predicted_interval/raw.txt";
        //if (DEBUG) {
        //  System.out.printf("importFutureRequests: going to import raw predictions from %s\n",raw_filename);
        //}

        FileReader file = new FileReader(raw_filename);
        BufferedReader input = new BufferedReader(file);
        String od_string = input.lines().collect(Collectors.joining());
        int[] od_arr = Arrays.stream(od_string.split(","))
                        .map(String::trim)
                        .mapToInt(Integer::parseInt)
                        .toArray();
        input.close();

        if (DEBUG) {
            System.out.printf("importFutureRequests: loaded raw prediction array of length %d\n",od_arr.length);
        }      

        if (od_arr.length == this.dm_predictions_raw.length)
        {
            System.arraycopy(od_arr, 0, this.dm_predictions_raw, 0, od_arr.length);
            if (DEBUG) {
                System.out.printf("importFutureRequests: updated raw predictions\n");
            }
        }

    } catch (Exception e) {
        System.err.printf("Error occurred when trying to import raw predictions\n");
        e.printStackTrace();
        throw e;        
    }

    //TODO: probability distributions, and sampled requests?
    }

    //this is the method which each individual model has to implement
    abstract public void runDemandModel() throws IOException, InterruptedException;


    public DemandPredictionModule(int height, int width, boolean debug, Communicator comm) {
        communicator = comm;
        DEBUG = debug;
        dm_height = height;
        dm_width = width;
        dm_predictions_raw  = new int[(dm_height*dm_width) * dm_height * dm_width];
        jargo_to_dm = loadNodeMapping("./demand_model_data/mapping_nodes/node_grid_map_5x20.csv");
        if (DEBUG) {
          System.out.printf("loaded Jargo-to-DemandModel node map\n");
        } 
    }
 

 }