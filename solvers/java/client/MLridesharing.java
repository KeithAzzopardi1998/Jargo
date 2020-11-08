package com.github.jargors.client;
import com.github.jargors.sim.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
public class MLridesharing extends Client {
  final int MAX_PROXIMITY = 1800;
  public void init() {
    System.out.printf("Set MAX_PROXIMITY=%d\n", MAX_PROXIMITY);
    this.batch_processing=true;
  }
  protected void handleRequestBatch(Object[] rb) throws ClientException, ClientFatalException {
    if (DEBUG) {
      System.out.printf("processing batch of size %d\n", rb.length);

      
    }
  }
}
