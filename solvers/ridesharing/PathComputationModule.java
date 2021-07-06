package ridesharing; 
import com.github.jargors.sim.*;

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
     public int[] getPath(int u, int v, int t, Tools tools)
      throws ClientException
     {
       return new int[10];
     }
   }    
}