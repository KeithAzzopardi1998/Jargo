package solvers;
public interface RequestCollectionModule {
    public int[][] getRequests();
    
    class ImmediateRCM implements RequestCollectionModule {
        public int[][] getRequests() {
            return new int[10][10];
        }
    }

    class ImmediateAndSampledRCM implements RequestCollectionModule {
        public int[][] getRequests() {
            return new int[10][10];
        }
    }    

}
