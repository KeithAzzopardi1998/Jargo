package ridesharing; 
import com.github.jargors.sim.*;
import com.github.jamjpan.libgtree_jni.GTree;
import com.github.jamjpan.libgtree_jni.gtreeJNI;

import com.github.jamjpan.libgtree_jni.IntVector;
import java.io.FileNotFoundException;
import java.io.File;
import java.util.*;

import javax.xml.crypto.dsig.keyinfo.RetrievalMethod;

public interface PathComputationModule {
    //takes a source node, destination node and time
    //and returns the best path to follow, where 
    //"best" is defined based on the actual algorithm implementation
    public int[] getPath(int u, int v, int t, Tools tools) throws ClientException;

    class ShortestPCM implements PathComputationModule {
        public int[] getPath(int u, int v, int t, Tools tools)
            throws ClientException
        {
            try {
            return tools.computeRoute(u, v, t); 
            } catch (Exception e) {
                throw new ClientException(e);
            }
        }
    }

    class MaxScorePCM implements PathComputationModule {
        private GTree gtree;

        private boolean flag_gtree_loaded = false;
    
        private Vector<Integer> nodes = new Vector<Integer>();
        //the edges will be stored as 2 vectors for each node:
        // - one with the node ID of each neighbour
        // - one with the weight of the edge connecting it to the corresponding neighbor
        private HashMap<Integer,Vector<Integer>> edges = new HashMap<Integer,Vector<Integer>>(); 
        private HashMap<Integer,Vector<Integer>> edgeWeights = new HashMap<Integer,Vector<Integer>>();  
    
        Integer num_time_bins =100;
        double alpha = 1.3;


        public void EdgeFileLoad(final String p) {
            try {
          int line_counter=1;
                Scanner scanner = new Scanner(new File(p));
                while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            //skip the first line
            if (line_counter==1){
                line_counter++;
                continue;
    
            }
            String[] numbers = line.split(" ");
            if (numbers.length!=3){
                System.out.printf("Invalid # of elements in line %d\n",line_counter);
                continue;
            }
            Integer src = Integer.parseInt(numbers[0]);
            Integer dest = Integer.parseInt(numbers[1]);
            Integer weight = Integer.parseInt(numbers[2]);
    
            //adding any unknown nodes
            if (this.nodes.contains(src) == false) {
                this.nodes.add(src);
            }
            if (this.nodes.contains(dest) == false) {
                this.nodes.add(dest);
            }
    
            //if the source node already has edges associated with it,
            //fetch the vector so that we can append to it.
            //If not, create a new vector for this edge
            Vector<Integer> edge_temp = edges.getOrDefault(src, new Vector<Integer>());
            edge_temp.add(dest);
            //overwrite the entry for this node, or create a new one
            edges.put(src, edge_temp);
    
            //repeat for the corresponding edge weight
            Vector<Integer> edgeWeight_temp = edgeWeights.getOrDefault(src, new Vector<Integer>());
            edgeWeight_temp.add(weight);
            edgeWeights.put(src, edgeWeight_temp);
    
            line_counter++;
                }
                scanner.close();
    
          int edge_counter=0;
          for(Map.Entry<Integer,Vector<Integer>> map_entry:edges.entrySet()){  
            Integer src_node_id = map_entry.getKey();
            Vector<Integer> neighbour_ids = map_entry.getValue();
            Vector<Integer> neighbour_weights = edgeWeights.get(src_node_id);
            for(int i=0; i < neighbour_ids.size(); i++){
              Integer i_id = neighbour_ids.get(i);
              Integer i_weight = neighbour_weights.get(i);
              //System.out.printf(" %d (%d)",i_id,i_weight);
              edge_counter++;
            }
          }  
          System.out.printf("finished loading edge file (#edges %d, #nodes %d)\n",edge_counter, this.nodes.size());
    
    
          
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }        
        }
    
        public void GTGtreeLoad(final String p) {
            try {
              System.loadLibrary("gtree_jni");
            } catch (UnsatisfiedLinkError e) {
              System.err.println("Native code library failed to load: "+e);
              System.exit(1);
            }
            if (p.length() > 0) {
              gtreeJNI.setIndex_path(p);
              this.gtree = new GTree();
              gtreeJNI.read_GTree(gtree);
              this.flag_gtree_loaded = true;
            } else {
              System.out.println("invalid path");
            }
        }
    
        public void GTGtreeClose() {
            this.gtree = null;
            this.flag_gtree_loaded = false;
        }
    
        // takes and returns "jargo-compatible" nodes, so values have to be
        // incremented/decremented accordingly to work with the GTree
        public int[] computeShortestPath(final Integer u, final Integer v)
        {
          int[] output = null;
          if (!this.flag_gtree_loaded) {
            System.out.println("Gtree not loaded!");
          } else if (u == 0) {
            System.out.println("Source cannot be 0!");
          } else if (v == 0) {
            output = new int[] { u, v };
          } else if (u == v) {
            output = new int[] { u };
          } else {
            IntVector path = new IntVector();
            System.out.println("running shortest_path_querying");
            gtree.shortest_path_querying((u - 1), (v - 1)); // L1
            System.out.println("running path_recovery");
            gtree.path_recovery((u - 1), (v - 1), path);
            if (path != null) {
              output = new int[(int)path.size()];
              for (int i = 0; i < path.size(); i++) {
                output[i] = path.get(i) + 1;                // L2
              }
            }
          }
          return output;
        }
    
        // takes and returns "jargo-compatible" nodes, so values have to be
        // incremented/decremented accordingly to work with the GTree
        public int computeShortestPathDistance(final Integer u, final Integer v)
        {
          int d = 0;
          if (!this.flag_gtree_loaded) {
            System.out.println("GTree not loaded!");
          } else if (u == 0) {  
            System.out.println("Source cannot be 0!");
          } else if (u != v && v != 0) {
            d = gtree.shortest_path_querying((u - 1), (v - 1));
          }
          return d;
        }
    
        Vector<Vector<Integer>> getDAGnodes(final Integer source, final Integer dest)
        {
            long startTime, endTime;
    
            Vector<Vector<Integer>> dag_nodes =  new Vector<Vector<Integer>>();
    
            int sp_dist = computeShortestPathDistance(source+1,dest+1);
            System.out.printf("getDAGnodes: SP distance is %d\n",sp_dist);
            //fetching the nodes which can be included in the DAG
            //iterating using the enhanced for loop
            startTime = System.currentTimeMillis();
            for(Integer v : this.nodes){
                Integer dist_to_dest = computeShortestPathDistance(v+1,dest+1);
                if (dist_to_dest <= sp_dist) {
                    Vector<Integer> node_info = new Vector<Integer>();
                    node_info.add(-dist_to_dest);
                    node_info.add(v);
                    dag_nodes.add(node_info);
                }
            }
            endTime = System.currentTimeMillis();
            System.out.printf("getDAGnodes: getting DAG nodes took %d ms\n",(endTime-startTime));
    
            startTime = System.currentTimeMillis();
            Collections.sort(dag_nodes, new Comparator<Vector<Integer>>(){
                @Override  public int compare(Vector<Integer> v1, Vector<Integer> v2) {
                    return v1.get(0).compareTo(v2.get(0)); //If you order by 2nd element in row
            }});
            endTime = System.currentTimeMillis();
            System.out.printf("getDAGnodes: sorting DAG nodes took %d ms\n",(endTime-startTime));
            //System.out.printf("getDAGnodes: sorted DAG:\n");
            //for(Vector<Integer> v : dag_nodes){
            //    System.out.printf("%d ",v.get(1));
            //}
    
            System.out.printf("getDAGnodes: DAG contains %d nodes\n",dag_nodes.size());
            return dag_nodes;
        }
    
        public int fetchNodeScoreMock(int node_index)
        {
          return 1;
        }
    
        public Integer[] getDAGpath(int source, int dest, Vector<Vector<Integer>> dag_nodes, int num_time_bins, int delta_dist)
        {
    
            //these two maps allow us to switch between DAG node index
            //and actual node index
            HashMap<Integer,Integer> dagIndexToNode= new HashMap<Integer,Integer>();
            HashMap<Integer,Integer> nodeToDagIndex = new HashMap<Integer,Integer>();
            for(int i = 0;i < dag_nodes.size();  ++i)
            {
                //System.out.printf("i=%d\n",i);
                dagIndexToNode.put(i,dag_nodes.get(i).get(1));
                nodeToDagIndex.put(dag_nodes.get(i).get(1),i);
            }
    
            //this is the DP table, and it has 3 dimensions:
            // D1 -> 1 entry for each node in the DP
            // D2 -> 1 entry for each value of "b", the BINNED values of "j"
            // D3 -> holds the actual info for this DP step, and has 4 entries per step
            //          0 -> new score, i.e. "c" in the paper           | scores.first
            //          1 -> updated distance travelled for this bin    | scores.second
            //          2 -> income node index (used for backtracking)  | store.first
            //          3 -> income bin index (used for backtracking    | store.second
            Integer[][][] dp_table = new Integer[dag_nodes.size()][num_time_bins][4];
            
            //initializations of c and j as defined in equation 11 of the paper
            for(int i=0 ; i<dp_table.length ; i++){
              for(int j=0 ; j<dp_table[i].length ; j++){
                  for(int k=0 ; k<dp_table[i][j].length ; k++){
                    //this represents negative infinity
                    dp_table[i][j][k] = Integer.MIN_VALUE;
                  }
              }
            }
            Integer startIndex = nodeToDagIndex.get(source);
            Integer endIndex = nodeToDagIndex.get(dest);
            dp_table[startIndex][0][0] = 0;
            dp_table[startIndex][0][1] = 0;
            dp_table[startIndex][0][2] = -1;
            dp_table[startIndex][0][3] = -1;
    
            //the DAG is sorted already, so we are guaranteed that it is 
            //traversed in the correct order	
            for(int i = startIndex; i < dag_nodes.size(); i++) 
            {
              //picking the node which we are working on
              //for this iteration of the loop
              Integer u = dag_nodes.get(i).get(1);
              Vector<Integer> u_neighbors = edges.get(u);
              Vector<Integer> u_neighbor_weights = edgeWeights.get(u);
    
              //looping through the time bins
              for(int k = 0; k < num_time_bins ; k++)
              {
                if(dp_table[i][k][0]<0)
                  continue;
                
                //this loop is used to loop through the neighbouring nodes
                //of the current node we are looking at (u)
                for (int j=0; j < u_neighbors.size(); j++)
                {
                  Integer v = u_neighbors.get(j);
                  Integer edge_dist = u_neighbor_weights.get(j);
    
                  //sanity checks
                  if (u == v) continue;
                  if (!nodeToDagIndex.containsKey(v)) continue;
    
                  Integer vIndex = nodeToDagIndex.get(v);
    
                  Integer prev_dist = dp_table[i][k][1];
    
                  // THIS IS IMPORTANT: WE ARE ASSUMING CONSTANT SPEED
                  //newTime is the bin "b". "edge_dist + prev_dist" is "j",
                  //so this follows the note in page 2262
                  int newTime= num_time_bins * (edge_dist + prev_dist)/delta_dist;
    
                  //"vIndex > i" ensures that we only move "forward" in the sorted DAG
                  //to avoid unncessary computations which we already have done
                  //if newtime > DISCRETE_TIME, this means that "edge_dist + prev_dist"
                  //is greater than delta_dist, implying that we're violating some
                  //detour constraint somewhere, making this calculation invalid 
                  //either way
                  if(newTime <= num_time_bins && vIndex > i )
                  {
                    //the total distance travelled so far if we choose to travel to v 
                    Integer newDist = edge_dist + prev_dist;
    
                    //fetch node score of v
                    Integer node_score = fetchNodeScoreMock(v);
                  
    
                    //we want to check the following condition: ( C1 or (C2 and C3) ), where C1, C2 and C3 are true if:
                    // C1 -> when we add the destination node's score to the score obtained so far at the current node/path,
                    //		 it EXCEEDS the current best score for the destination-distance combination (recall that this was initialized to -infinity)
                    // C2 -> when we add the destination node's score to the score obtained so far at the current node/path,
                    //		 it IS EQUAL TO the current best score for the destination-distance combination (recall that this was initialized to -infinity)
                    // C3 -> the new distance is lower than the current distance considered for this time bin
                    if (
                      ( dp_table[vIndex][newTime][0] < (dp_table[i][k][0]+node_score) ) //C1
                      ||
                      (
                        ( dp_table[vIndex][newTime][0] == (dp_table[i][k][0]+node_score) ) //C2
                        &&
                        ( newDist < dp_table[vIndex][newTime][1]) //C3
                      )
                    )
                    {
                      //if the condition passes, update the entries of the DP table
                      dp_table[vIndex][newTime][0] = dp_table[i][k][0]+node_score;
                      dp_table[vIndex][newTime][1] = newDist;
                      dp_table[vIndex][newTime][2] = i;
                      dp_table[vIndex][newTime][3] = k;
                    }
    
                  } 
                }
              }
            }
    
            //BACKTRACKING
            List<Integer> path = new ArrayList<Integer>();
    
            //determining the starting point for backtracking by looping through
            //the time bins for the destination node (at endIndex) and finding
            //the highest score
            Integer max_score=-1;
            Integer max_time=-1;
            for( int popp=0;popp<num_time_bins;popp++) 
            {
              if( dp_table[endIndex][popp][0]>max_score ) 
              {
                max_score= dp_table[endIndex][popp][0];
                max_time= popp;
              }
            }
          
            if(max_score==-1)
            {
              System.out.printf("Error when backtracking\n");
              return null;
            }
    
            //this is the actual backtracking part to build the path
            Integer traceLocation = endIndex;
            Integer tracetime = max_time;
            while (traceLocation != -1) 
            {
              //printf(" At %lld[%lld]: Score = %d (dist: %.4f)\n",traceLocation, nodeToDagIndex[traceLocation], traceScore, scores[ nodeToDagIndex[traceLocation] ][traceScore].first);
              path.add(dagIndexToNode.get(traceLocation) );
              Integer prevtime = dp_table[ traceLocation ][ tracetime ][3];
              traceLocation = dp_table[ traceLocation ][tracetime][2];
              tracetime = prevtime;
            }
    
            Integer[] arr = new Integer[path.size()];
            arr = path.toArray(arr);
            return arr;
        }
    
        // takes and returns "jargo-compatible" nodes, so values have to be
        // incremented/decremented accordingly to work with the GTree
        public int[] computeDAGPath(final Integer u, final Integer v, final Integer num_bins, final Integer delta_dist)
        {
            System.out.printf("computeDAGPath: calculating path from node %d to node %d\n",u,v);
            Vector<Vector<Integer>> dag_nodes = getDAGnodes(u-1,v-1);
    
            //path with GTree indexing
            Integer[] path_gtree = getDAGpath(u-1,v-1,dag_nodes, num_bins, delta_dist);
    
            //we need to increment each value to match the Jargo indexing
            //of nodes, and reverse the DAG path obtained through backtracking
            int[] path = new int[path_gtree.length];
            for(int i = 0; i < path.length; i++)
            {
              path[i] = path_gtree[path.length - (i+1)]+1;
            }
            return path;
        }

        public MaxScorePCM()
        {
            GTGtreeLoad("/home/keith/Dissertation/testing_gtree_maxscore/mny.gtree");
            EdgeFileLoad("/home/keith/Dissertation/testing_gtree_maxscore/mny.edges");
        }

        public int[] getPath(int u, int v, int t, Tools tools)
            throws ClientException
        {
            try {
                if (u == 0) {
                    throw new GtreeIllegalSourceException("Source cannot be 0!");
                } else if (v == 0) {
                    return new int[] { t, u, t + 1, v };
                } else {
                    int shortest_path_dist = computeShortestPathDistance(u, v);
                    int delta_dist = (int)(shortest_path_dist * alpha);
                    return computeDAGPath(u, v, num_time_bins,delta_dist);
                }                
            } catch (Exception e) {
                throw new ClientException(e);
            }
        }
    }    
}