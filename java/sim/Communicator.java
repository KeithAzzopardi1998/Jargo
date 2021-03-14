package com.github.jargors.sim;
import com.github.jargors.sim.Storage;
import com.github.jargors.sim.Controller;
import com.github.jargors.sim.Traffic;
import com.github.jargors.sim.ClientException;
import com.github.jargors.sim.ClientFatalException;
import com.github.jargors.sim.DuplicateVertexException;
import com.github.jargors.sim.DuplicateEdgeException;
import com.github.jargors.sim.DuplicateUserException;
import com.github.jargors.sim.EdgeNotFoundException;
import com.github.jargors.sim.UserNotFoundException;
import com.github.jargors.sim.VertexNotFoundException;
import com.github.jargors.sim.RouteIllegalOverwriteException;
import com.github.jargors.sim.TimeWindowException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.sql.SQLException;
public class Communicator {
  private Storage storage;
  private Controller controller;
  private Traffic traffic = null;
  private final boolean DEBUG = "true".equals(System.getProperty("jargors.communicator.debug"));
  public Communicator() { }
  public int[] queryEdge(final int v1, final int v2) throws EdgeNotFoundException, SQLException {
           int[] output = this.storage.DBQueryEdge(v1, v2);
           return output;
         }
  public int[] queryMBR() throws SQLException {
           int[] output = this.storage.DBQueryMBR();
           return output;
         }
  public int[] queryMetricCountAssigned() {
           return this.storage.DBQueryMetricCountAssigned();
         }
  public int[] queryMetricServerDistanceTotal() throws SQLException {
           return queryMetricServerDistanceTotal(true);
         }
  public int[] queryMetricServerDistanceTotal(boolean flag_usecache) throws SQLException {
           int[] output = storage.DBQueryMetricServerDistanceTotal(flag_usecache);
           return output;
         }
  public int[] queryMetricServiceRate() throws SQLException {
           return queryMetricServiceRate(true);
         }
  public int[] queryMetricServiceRate(boolean flag_usecache) throws SQLException {
           int[] output = storage.DBQueryMetricServiceRate(flag_usecache);
           return output;
         }
  public int[] queryMetricRequestDistanceBaseAssigned() {
           return this.storage.DBQueryMetricRequestDistanceBaseAssigned();
         }
  public int[] queryServerCapacityViolations(final int sid,
             final int rq, final int tp, final int td) throws SQLException {
           return this.storage.DBQueryServerCapacityViolations(sid, rq, tp, td);
         }
  public int queryServerCapacity(final int sid)
             throws SQLException {
           return -1 * this.storage.DBQueryUserCapacity(sid)[0];
         }         
  public int[] queryServerDistanceRemaining(final int sid, final int t) throws SQLException {
           int[] output = this.storage.DBQueryServerDistanceRemaining(sid, t);
           return output;
         }
  public int[] queryServerDurationRemaining(final int sid, final int t) throws SQLException {
           int[] output = this.storage.DBQueryServerDurationRemaining(sid, t);
           return output;
         }
  public int[] queryServerDurationTravel(final int sid, boolean flag_usecache) throws SQLException {
           return storage.DBQueryServerDurationTravel(sid, flag_usecache);
         }
  public int[] queryServerLoadMax(final int sid, final int t) throws SQLException {
           int[] output = this.storage.DBQueryServerLoadMax(sid, t);
           return output;
         }
  public int[] queryServerRouteActive(final int sid) throws SQLException {
           int[] output = this.storage.DBQueryServerRouteActive(sid);
           return output;
         }
  public int[] queryServerRouteRemaining(final int sid, final int t) throws SQLException {
           int[] output = this.storage.DBQueryServerRouteRemaining(sid, t);
           return output;
         }
  public int[] queryServerScheduleRemaining(final int sid, final int t) throws SQLException {
           int[] output = this.storage.DBQueryServerScheduleRemaining(sid, t);
           return output;
         }
  public int[] queryServersCount() throws SQLException {
           int[] output = storage.DBQueryServersCount();
           return output;
         }
  public int[] queryServersLocationsActive(final int t) throws SQLException {
           int[] output = this.storage.DBQueryServersLocationsActive(t);
           return output;
         }
  public int[] queryUser(final int rid) throws UserNotFoundException, SQLException {
           int[] output = storage.DBQueryUser(rid);
           return output;
         }
  public int[] queryVertex(final int v) throws VertexNotFoundException, SQLException {
           int[] output = this.storage.DBQueryVertex(v);
           return output;
         }
  public int[] queryRequestsInInterval(final int t_start, final int t_end) throws SQLException {
           int[] output = this.storage.DBQueryRequestsInInterval(t_start,t_end);
           return output;
         }
  public void updateServerService(final int sid, final int[] route, final int[] sched,
             final int[] ridpos, final int[] ridneg)
         throws RouteIllegalOverwriteException, UserNotFoundException,
                EdgeNotFoundException, TimeWindowException, SQLException {
           final int[] current = this.storage.DBQueryServerRoute(sid);
           int t_now  = this.retrieveClock();
           int t_next = t_now;
           for (int i = 0; i < (current.length - 1); i += 2) {
             if (current[i] > t_next) {
               t_next = current[i];
               break;
             }
           }
           long time_start = System.currentTimeMillis();//TODO remove this
           int i = 0;
           while (i < current.length && current[i] != route[0]) {
             i += 2;
           }
           if (i == current.length) {
             if (DEBUG) {
               for (i = 0; i < current.length - 1; i+=2) {
                 System.out.printf("debug wold[%d..%d]={ %d, %d }\n", i, (i + 1),
                     current[i], current[i+1]);
               }
               for (i = 0; i < route.length - 1; i+=2) {
                 System.out.printf("debug wnew[%d..%d]={ %d, %d }\n", i, (i + 1),
                     route[i], route[i+1]);
               }
             }
             throw new RouteIllegalOverwriteException("Missing branch point!");
           }
           int j = 0;
           while (i < current.length && (current[i] <= t_next && current[(i + 1)] != 0)) {
             if ((current[(i + 1)] != route[(j + 1)])
              || (current[i] != route[j] && current[i] <= t_now)) {
               if (DEBUG) {
                 System.out.printf("overwrite, current[%d] != route[%d] or current[%d] != route[%d]\n",
                     i, j, (i + 1), (j + 1));
                 for (i = 0; i < current.length - 1; i+=2) {
                   System.out.printf("debug wold[%d..%d]={ %d, %d }\n", i, (i + 1),
                       current[i], current[i+1]);
                 }
                 for (i = 0; i < route.length - 1; i+=2) {
                   System.out.printf("debug wnew[%d..%d]={ %d, %d }\n", i, (i + 1),
                       route[i], route[i+1]);
                 }
               }
               throw new RouteIllegalOverwriteException("Overwrite occurred!");
             }
             i += 2;
             j += 2;
           }
           long time_end = System.currentTimeMillis();//TODO remove this
           if (DEBUG) {
             System.out.printf("updateServerService -> checking for exceptions took %d ms\n",(time_end-time_start));
           }
           /*for (int k = 0; k < (sched.length - 2); k += 3) {
               final int tl = this.storage.DBQueryUser(sched[(k + 2)])[3];
               if (sched[k] > tl) {
                 throw new TimeWindowException("Waypoint time (t="+sched[k]+") "
                     +"after late window (t="+tl+", uid="+sched[(k + 2)]+")");
               }
             }*/
           time_start = System.currentTimeMillis();//TODO remove this
           
           int[] mutroute = route.clone();
           int[] mutsched = sched.clone();
           if (this.traffic != null) {
             //traversing the new route in pairs
             for (int k = 0; k < (mutroute.length - 3); k += 4) {
               final int t1 = mutroute[k];//time at vertex 1
               final int v1 = mutroute[(k + 1)];//vertex 1
               final int t2 = mutroute[(k + 2)];//time at vertex 2
               final int v2 = mutroute[(k + 3)];//vertex 2
               int[] ddnu = this.storage.DBQueryEdge(v1, v2);//distance and speed to travel the edge?
               final int dd = ddnu[0];//distance
               final int nu_old = ddnu[1];//speed
               final int nu_new = Math.max(1, 
                   (int) Math.round(this.traffic.apply(
                       v1, v2, (1000*t1 + this.controller.getClockReferenceMs())
                   )*nu_old));//speed with traffic
               final int diff = ((dd/(t2 - t1)) > nu_new
                   ? ((int) Math.ceil((dd/(float) nu_new + t1))) - t2
                   : 0);
               if (diff != 0) {
                 for (int p = 0; p < (mutsched.length - 3); p += 4) {
                   if (mutsched[p] >= mutroute[(k + 2)]) {
                     mutsched[p] += diff;
                   }
                 }
                 for (int q = (k + 2); q < (mutroute.length - 1); q += 2) {
                   mutroute[q] += diff;
                 }
               }
             }
           }
           time_end = System.currentTimeMillis();//TODO remove this
           if (DEBUG) {
             System.out.printf("updateServerService -> updating route with traffic took %d ms\n",(time_end-time_start));
           }

           time_start = System.currentTimeMillis();//TODO remove this
           this.storage.DBUpdateServerService(sid, mutroute, mutsched, ridpos, ridneg);
           time_end = System.currentTimeMillis();//TODO remove this
           if (DEBUG) {
             System.out.printf("updateServerService -> call to DBUpdateServerService took %d ms\n",(time_end-time_start));
           }
         }
  public int retrieveClock() {
           return this.controller.getClock();
         }
  public final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, int[]>> retrieveRefCacheEdges() {
           return this.storage.getRefCacheEdges();
         }
  public final ConcurrentHashMap<Integer, int[]> retrieveRefCacheUsers() {
           return this.storage.getRefCacheUsers();
         }
  public final ConcurrentHashMap<Integer, int[]> retrieveRefCacheVertices() {
           return this.storage.getRefCacheVertices();
         }
  public void setRefController(final Controller controller) {
           this.controller = controller;
         }
  public void setRefStorage(final Storage storage) {
           this.storage = storage;
         }
  public void setRefTraffic (final Traffic traffic) {
           this.traffic = traffic;
           this.traffic.forwardRefCacheVertices(this.storage.getRefCacheVertices());
           this.traffic.forwardRefCacheEdges(this.storage.getRefCacheEdges());
         }
  public void forwardReturnRequest(final int[] r) {
           this.controller.returnRequest(r);
         }
  public void kill() {
           this.controller.kill();
         }
}
