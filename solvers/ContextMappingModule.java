package solvers;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import com.github.jargors.sim.Communicator;
import com.github.jargors.sim.Tools;

public class ContextMappingModule {
    private int MAXN;

    public ContextMappingModule(int _maxn){
        MAXN = _maxn;
    }

    public Integer[][] getContextMap(int[][] rb, Communicator _communicator, Tools _tools, ConcurrentHashMap<Integer, Integer> lut, ConcurrentHashMap<Integer, Integer> luv){
        Integer [][] contextMapping = new Integer[rb.length][2*MAXN];

        //get the list of available vehicles
        //store a copy of all vehicles and their last recorded times inside "candidates_all"
        Map<Integer, Integer> candidates_all = new HashMap<Integer, Integer>(lut);
        
        //1. filter out vehicles which are full
        // ?? do we need to do this? ("full" depends on the insertion point)
        
        //2. distinguish between vehicles which are idle (i.e. current route is empty)
        //   and those which aren't (i.e. already have scheduled pickups and/or dropoffs)
        List<Integer> candidates_idle = new ArrayList<Integer>();
        List<Integer> candidates_enroute = new ArrayList<Integer>();
        for (final int sid : candidates_all.keySet()) {
        //see comments inside GreedyInsertion.java for the reason why we are 
        //checking if wact[3] is 0 to check whether the vehicle is idle or not
        final int[] wact = _communicator.queryServerRouteActive(sid);
        if (wact[3] == 0) {
            candidates_idle.add(sid);
        } else {
            candidates_enroute.add(sid);
        }
        }
        //if (DEBUG) {
        //  System.out.printf("Got %d idle vehicles and %d enroute vehicles\n",candidates_idle.size(),candidates_enroute.size());
        //}
    
        //TODO: check what happens if the length of candidates_idle
        //      or candidates_enroute is less than MAXN
    
        //3. for each request:
        for (int r_index = 0; r_index < contextMapping.length; r_index++)
        {
        final int r_origin = rb[r_index][4];

        //a. from the list of idle vehicles, pick the closest "maxn" ones
        final int n_closest_candidates = candidates_idle.size() < MAXN
                                ? candidates_idle.size()
                                : MAXN;
        Integer[] closest_candidates = closest_n_vehicles(r_origin,
                                        (Integer[])candidates_idle.toArray(new Integer[candidates_idle.size()]),
                                        n_closest_candidates, _tools, luv);                              
        for (int i1 = 0; i1 < n_closest_candidates; i1++) {
            contextMapping[r_index][i1] = closest_candidates[i1];
        }
        for (int i2 = n_closest_candidates; i2 < MAXN; i2++ ) {
            contextMapping[r_index][i2] = -1;
        }

        //b. from the list of non-idle vehicles, pick "maxn" ones at random
        //TODO: speed up by only shuffling the number of elements we need
        //      (but make sure we don't end up picking duplicates)
        Collections.shuffle(candidates_enroute);
        final int n_random_candidates = candidates_enroute.size() < MAXN
                                ? candidates_enroute.size()
                                : MAXN;
        for (int j1 = 0; j1 < n_random_candidates; j1++) {
            contextMapping[r_index][MAXN+j1] = candidates_enroute.get(j1);
        }
        for (int j2 = MAXN+n_random_candidates; j2 < 2*MAXN; j2++ ) {
            contextMapping[r_index][j2] = -1;
        }
        
        }

        return contextMapping;
          
    }   
    
    //TODO benchmark against something found online
  protected Integer[] closest_n_vehicles(final int vertex, final Integer[] vehicle_ids , final int n, Tools _tools, ConcurrentHashMap<Integer, Integer> luv){
    Integer[] sorted_ids = new Integer[n];
    Arrays.fill(sorted_ids,-1);//fill with an invalid vehicle ID
    Integer[] sorted_values = new Integer[n];
    Arrays.fill(sorted_values,Integer.MAX_VALUE);//fill with the highest possible value since we want a list of the smallest values

    //for the first entry there's nothing to compare, so we just insert it into the
    //array of sorted items. This is why the following loop starts at 1
    sorted_ids[0]=vehicle_ids[0];
    sorted_values[0]=_tools.computeHaversine(luv.get(vehicle_ids[0]), vertex);

    //used as a temporary space when swapping two elements during the sort
    Integer tmp_swp=0;

    //loop through the vehicles
    for (int v_index = 1; v_index < vehicle_ids.length; v_index++) {
    //get the vehicle ID and its distance to the vertex of interest
    Integer v_id = vehicle_ids[v_index];
    final int dist = _tools.computeHaversine(luv.get(v_id), vertex);
    if (dist > 0){//just a sanity check
        
        //we use a bubble-sort like technique (ascending), but the sort is only performed
        //if the calculated value is less than the greatest value in the subset, which will
        //always be at the very end of the array since we maintain a sorted list.
        //this is computationally efficient since n, the length of the subset we want,
        //is usually going to be much smaller than the length of the vehicle id array
        //i.e. as we progress, the "bubble sort" part will happen less and less often,
        //because this if statement won't pass
        if (dist < sorted_values[n-1]) {
        //place the element at the last location
        sorted_ids[n-1]=v_id;
        sorted_values[n-1]=dist;
        //"bubble" through the rest of the array, until we find a value which is less
        //than the current distance. temp_n keeps track of the current location of the entry
        //as it bubbles through the array
        int temp_n=n-1;
        while ( (temp_n>0) && sorted_values[temp_n-1]>sorted_values[temp_n] ) {
            //swap out the values at temp_n and temp_n-1
            tmp_swp=sorted_ids[temp_n];//swapping the IDs
            sorted_ids[temp_n]=sorted_ids[temp_n-1];
            sorted_ids[temp_n-1]=tmp_swp;

            tmp_swp=sorted_values[temp_n];//swapping the values
            sorted_values[temp_n]=sorted_values[temp_n-1];
            sorted_values[temp_n-1]=tmp_swp;
            
            temp_n--;
        }
        }

    }
    }
    return sorted_ids;

  }

}
