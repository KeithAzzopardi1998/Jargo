package ridesharing;
import com.github.jargors.sim.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class DNNModel extends DemandPredictionModule {
    public DNNModel(int height, int width, boolean debug, Communicator comm) {
        super(height, width, debug, comm);
    }

    // runs the demand prediction model script
    public void runDemandModel() throws IOException, InterruptedException{
        try{
            String command = "./demand_model_data/environment/venv/bin/python3"
                            + " ./demand_model_data/scripts/predict_1Darray.py"
                            + " --in1 ./demand_model_data/input_intervals/interval_1.txt"
                            + " --in2 ./demand_model_data/input_intervals/interval_2.txt"
                            + " --in3 ./demand_model_data/input_intervals/interval_3.txt"
                            + " --in4 ./demand_model_data/input_intervals/interval_4.txt"
                            + " --in5 ./demand_model_data/input_intervals/interval_5.txt"
                            + " --model_file ./demand_model_data/model_files/odonly_20x5_cont.h5"
                            + " --out_raw ./demand_model_data/predicted_interval/raw.txt";
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
}
