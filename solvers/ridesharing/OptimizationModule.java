package ridesharing;

public class OptimizationModule {

    //uses the Hungarian Algorithm to calculate the assignments
    //for a given cost matrix
    public int[] getAssignments(double[][] cost_matrix){
        HungarianAlgorithm optModule = new HungarianAlgorithm(cost_matrix);
        int[] assignments = optModule.execute();
        return assignments;
    }
}
