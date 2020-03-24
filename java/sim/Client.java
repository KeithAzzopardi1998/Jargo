package com.github.jargors.sim;
import com.github.jargors.sim.Communicator;
import com.github.jargors.sim.Tools;
import com.github.jargors.sim.ClientException;
import com.github.jargors.sim.ClientFatalException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.io.FileNotFoundException;
public abstract class Client {
  protected ConcurrentLinkedQueue<int[]> queue = new ConcurrentLinkedQueue<int[]>();
  protected Communicator communicator;
  protected Tools tools = new Tools();
  protected final boolean DEBUG =
      "true".equals(System.getProperty("jargors.client.debug"));
  protected ConcurrentHashMap<Integer, Integer> lut = new ConcurrentHashMap<Integer, Integer>();
  protected ConcurrentHashMap<Integer, Integer> luv = new ConcurrentHashMap<Integer, Integer>();
  public Client() {
    if (DEBUG) {
      System.out.printf("create Client\n");
    }
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
  public int dropRequests(final int deadline) {
           final int temp = this.queue.size();
           this.queue.removeIf((r) -> { return r[2] < deadline; });
           return Math.max(0, temp - this.queue.size());
         }
  public void collectServerLocations(final int[] src) {
           for (int i = 0; i < (src.length - 2); i += 3) {
             this.handleServerLocation(new int[] {
               src[i],
               src[(i + 1)],
               src[(i + 2)]
             });
           }
         }
  public void notifyNew() throws ClientException, ClientFatalException {
           while (!this.queue.isEmpty()) {
             this.handleRequest(this.queue.remove());
             if (DEBUG) {
               System.out.printf("handleRequest(1), arg1=[#]\n");
             }
           }
         }
  public void init() { }
  protected void end() { }
  protected void handleRequest(final int[] r) throws ClientException, ClientFatalException { }
  protected void handleServerLocation(final int[] loc) {
              lut.put(loc[0], loc[1]);
              luv.put(loc[0], loc[2]);
            }
}
