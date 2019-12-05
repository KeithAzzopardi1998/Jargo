package com.github.jargors;
import com.github.jargors.Storage;
import com.github.jargors.Communicator;
import com.github.jargors.Client;
import com.github.jargors.Tools;
import com.github.jargors.exceptions.ClientException;
import com.github.jargors.exceptions.ClientFatalException;
import com.github.jargors.exceptions.DuplicateVertexException;
import com.github.jargors.exceptions.DuplicateEdgeException;
import com.github.jargors.exceptions.DuplicateUserException;
import com.github.jargors.exceptions.EdgeNotFoundException;
import com.github.jargors.exceptions.UserNotFoundException;
import com.github.jargors.exceptions.VertexNotFoundException;
import com.github.jargors.exceptions.GtreeNotLoadedException;
import com.github.jargors.exceptions.GtreeIllegalSourceException;
import com.github.jargors.exceptions.GtreeIllegalTargetException;
import com.github.jargors.jmx.*;
import java.lang.management.*;
import javax.management.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.Scanner;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Map;
import java.util.HashMap;
import java.sql.SQLException;
public class Controller {
  private Storage storage;
  private Communicator communicator;
  private Tools tools = new Tools();
  private Client client;
  private Map<Integer, Boolean> lu_seen = new HashMap<Integer, Boolean>();
  private int CLOCK_START =
      Integer.parseInt(System.getProperty("jargors.controller.clock_start", "0"));
  private int CLOCK_END =
      Integer.parseInt(System.getProperty("jargors.controller.clock_end", "1800"));
  private int REQUEST_TIMEOUT =
      Integer.parseInt(System.getProperty("jargors.controller.request_timeout", "30"));
  private int QUEUE_TIMEOUT =
      Integer.parseInt(System.getProperty("jargors.controller.queue_timeout", "30"));
  private int REQUEST_COLLECTION_PERIOD =
      Integer.parseInt(System.getProperty("jargors.controller.request_collection_period", "1"));
  private int REQUEST_HANDLING_PERIOD =
      Integer.parseInt(System.getProperty("jargors.controller.request_handling_period", "1"));
  private int SERVER_COLLECTION_PERIOD =
      Integer.parseInt(System.getProperty("jargors.controller.server_collection_period", "1"));
  private int loop_delay = 0;
  // private int deviation_rate = 0.02;
  // private int breakdown_rate = 0.005;
  private final double CSHIFT = Storage.CSHIFT;
  private boolean kill = false;
  private boolean working = false;
  private ScheduledExecutorService exe = null;
  private ScheduledFuture<?> cb1 = null;
  private ScheduledFuture<?> cb2 = null;
  private ScheduledFuture<?> cb3 = null;
  private ScheduledFuture<?> cb4 = null;
  private ScheduledFuture<?> cb5 = null;
  private final boolean DEBUG =
      "true".equals(System.getProperty("jargors.controller.debug"));
  private Runnable ClockLoop = () -> {
    // TODO: The speed of the updateServer.. methods is about 50ms, meaning we
    // can do ~20 updates per second. If a problem instance has more than 20
    // requests per second and an algo is fast enough to do more than 20 updates
    // per second, the updates will become the bottleneck. It might be unfair
    // to the algo if we advance the clock while waiting for updates to finish.
    // So in this case we only advance the clock after the updates finish.
    // How to implement? We just measure the time it takes to do an update and
    // add that duration onto the clock. We can output a "clock rate" to show
    // the user the current simulation rate, i.e. clock_rate=1x means real-time,
    // clock_rate=0.5x means 1 simulated second takes 2 real seconds, etc.
    this.statControllerClockNow++;
    this.statControllerClockReferenceSecond++;
    if (this.statControllerClockReferenceSecond > 59) {
      this.statControllerClockReferenceSecond = 0;
      this.statControllerClockReferenceMinute++;
      if (this.statControllerClockReferenceMinute > 59) {
        this.statControllerClockReferenceMinute = 0;
        this.statControllerClockReferenceHour++;
        if (this.statControllerClockReferenceHour > 23) {
          this.statControllerClockReferenceHour = 0;
          this.statControllerClockReferenceDay++;
        }
      }
    }
  };
  private Runnable RequestCollectionLoop = () -> {
    long A0 = System.currentTimeMillis();
    int  A1 = 0;
    try {
      int[] output = this.storage.DBQueryRequestsQueued(this.statControllerClockNow);
      for (int i = 0; i < (output.length - 6); i += 7) {
        if (!this.lu_seen.containsKey(output[i]) || this.lu_seen.get(output[i]) == false) {
          this.client.addRequest(new int[] {
            output[(i + 0)],
            output[(i + 1)],
            output[(i + 2)],
            output[(i + 3)],
            output[(i + 4)],
            output[(i + 5)],
            output[(i + 6)] });
          this.lu_seen.put(output[i], true);
          A1++;
        }
      }
    } catch (SQLException e) {
      if (e.getErrorCode() == 40000) {
        System.err.println("Warning: database connection interrupted");
      } else {
        System.err.println("Encountered fatal error");
        System.err.println(e.toString());
        System.err.println(e.getErrorCode());
        e.printStackTrace();
        System.exit(1);
      }
    }
        this.statControllerRequestCollectionCount++;
        this.statControllerRequestCollectionSizeLast = A1;
        this.statControllerRequestCollectionSizeTotal +=
        this.statControllerRequestCollectionSizeLast;
    if (this.statControllerRequestCollectionSizeLast <
        this.statControllerRequestCollectionSizeMin) {
        this.statControllerRequestCollectionSizeMin =
        this.statControllerRequestCollectionSizeLast;}
    if (this.statControllerRequestCollectionSizeLast >
        this.statControllerRequestCollectionSizeMax) {
        this.statControllerRequestCollectionSizeMax =
        this.statControllerRequestCollectionSizeLast;}
        this.statControllerRequestCollectionSizeAvg = (double)
        this.statControllerRequestCollectionSizeTotal/
        this.statControllerRequestCollectionCount;
        this.statControllerRequestCollectionDurLast = (System.currentTimeMillis() - A0);
        this.statControllerRequestCollectionDurTotal +=
        this.statControllerRequestCollectionDurLast;
    if (this.statControllerRequestCollectionDurLast <
        this.statControllerRequestCollectionDurMin) {
        this.statControllerRequestCollectionDurMin =
        this.statControllerRequestCollectionDurLast;}
    if (this.statControllerRequestCollectionDurLast >
        this.statControllerRequestCollectionDurMax) {
        this.statControllerRequestCollectionDurMax =
        this.statControllerRequestCollectionDurLast;}
        this.statControllerRequestCollectionDurAvg = (double)
        this.statControllerRequestCollectionDurTotal/
        this.statControllerRequestCollectionCount;
  };
  private Runnable RequestHandlingLoop = () -> {
    try {
      this.client.notifyNew();  // blocks this thread until queue is empty
    } catch (ClientException e) {
      System.err.printf("[t=%d] Controller.RequestHandlingLoop caught a ClientException: %s\n",
          this.statControllerClockNow, e.toString());
      e.printStackTrace();
    } catch (ClientFatalException e) {
      System.err.printf("[t=%d] Controller.RequestHandlingLoop caught a ClientFatalException: %s\n",
          this.statControllerClockNow, e.toString());
      e.printStackTrace();
      System.exit(1);
    }
  };
  private Runnable ServerLoop = () -> {
    try {
      int[] output = this.storage.DBQueryServersLocationsActive(this.statControllerClockNow);
      this.client.collectServerLocations(output);
    } catch (SQLException e) {
      if (e.getErrorCode() == 40000) {
        System.err.println("Warning: database connection interrupted");
      } else {
        System.err.println("Encountered fatal error");
        System.err.println(e.toString());
        System.err.println(e.getErrorCode());
        e.printStackTrace();
        System.exit(1);
      }
    }
  };
  private int    statControllerClockNow;
  private int    statControllerClockReferenceDay;
  private int    statControllerClockReferenceHour;
  private int    statControllerClockReferenceMinute;
  private int    statControllerClockReferenceSecond;
  private int    statControllerRequestCollectionCount = 0;
  private int    statControllerRequestCollectionSizeTotal = 0;
  private int    statControllerRequestCollectionSizeLast = 0;
  private int    statControllerRequestCollectionSizeMin = Integer.MAX_VALUE;
  private int    statControllerRequestCollectionSizeMax = 0;
  private double statControllerRequestCollectionSizeAvg = 0;
  private long   statControllerRequestCollectionDurLast = 0;
  private long   statControllerRequestCollectionDurMin = Integer.MAX_VALUE;
  private long   statControllerRequestCollectionDurMax = 0;
  private long   statControllerRequestCollectionDurTotal = 0;
  private double statControllerRequestCollectionDurAvg = 0;
  private int    statQueryEdgeCount    = 0;
  private long   statQueryEdgeDurLast  = 0;
  private long   statQueryEdgeDurTotal = 0;
  private long   statQueryEdgeDurMin   = Integer.MAX_VALUE;
  private long   statQueryEdgeDurMax   = 0;
  private double statQueryEdgeDurAvg   = 0;
  private int    statQueryServerRouteRemainingCount    = 0;
  private long   statQueryServerRouteRemainingDurLast  = 0;
  private long   statQueryServerRouteRemainingDurTotal = 0;
  private long   statQueryServerRouteRemainingDurMin   = Integer.MAX_VALUE;
  private long   statQueryServerRouteRemainingDurMax   = 0;
  private double statQueryServerRouteRemainingDurAvg   = 0;
  private int    statQueryServersLocationsActiveCount    = 0;
  private long   statQueryServersLocationsActiveDurLast  = 0;
  private long   statQueryServersLocationsActiveDurTotal = 0;
  private long   statQueryServersLocationsActiveDurMin   = Integer.MAX_VALUE;
  private long   statQueryServersLocationsActiveDurMax   = 0;
  private double statQueryServersLocationsActiveDurAvg   = 0;
  private int    statQueryUserCount    = 0;
  private long   statQueryUserDurLast  = 0;
  private long   statQueryUserDurTotal = 0;
  private long   statQueryUserDurMin   = Integer.MAX_VALUE;
  private long   statQueryUserDurMax   = 0;
  private double statQueryUserDurAvg   = 0;
  private int    statQueryVertexCount    = 0;
  private long   statQueryVertexDurLast  = 0;
  private long   statQueryVertexDurTotal = 0;
  private long   statQueryVertexDurMin   = Integer.MAX_VALUE;
  private long   statQueryVertexDurMax   = 0;
  private double statQueryVertexDurAvg   = 0;
  public Controller() {
    this.storage = new Storage();
    this.communicator = new Communicator();
    this.communicator.setRefStorage(this.storage);
    this.communicator.setRefController(this);
    try {
      MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
      ControllerMonitor mon = new ControllerMonitor(this);
      mbs.registerMBean(mon, new ObjectName("com.github.jargors.jmx:type=ControllerMonitor"));
    } catch (Exception e) {
      System.err.printf("ControllerMonitor failed; reason: %s\n", e.toString());
      System.err.printf("Continuing with monitoring disabled\n");
    }
  }
  public int    getControllerClockNow() {
           return this.statControllerClockNow;
         }
  public int    getControllerClockReferenceDay() {
           return this.statControllerClockReferenceDay;
         }
  public int    getControllerClockReferenceHour() {
           return this.statControllerClockReferenceHour;
         }
  public int    getControllerClockReferenceMinute() {
           return this.statControllerClockReferenceMinute;
         }
  public int    getControllerClockReferenceSecond() {
           return this.statControllerClockReferenceSecond;
         }
  public int    getControllerRequestCollectionSizeLast() {
           return this.statControllerRequestCollectionSizeLast;
         }
  public int    getControllerRequestCollectionSizeMin() {
           return this.statControllerRequestCollectionSizeMin;
         }
  public int    getControllerRequestCollectionSizeMax() {
           return this.statControllerRequestCollectionSizeMax;
         }
  public double getControllerRequestCollectionSizeAvg() {
           return this.statControllerRequestCollectionSizeAvg;
         }
  public long   getControllerRequestCollectionDurLast() {
           return this.statControllerRequestCollectionDurLast;
         }
  public long   getControllerRequestCollectionDurMin() {
           return this.statControllerRequestCollectionDurMin;
         }
  public long   getControllerRequestCollectionDurMax() {
           return this.statControllerRequestCollectionDurMax;
         }
  public double getControllerRequestCollectionDurAvg() {
           return this.statControllerRequestCollectionDurAvg;
         }
  public int    getStatQueryEdgeCount() {
           return this.statQueryEdgeCount;
         }
  public long   getStatQueryEdgeDurLast() {
           return this.statQueryEdgeDurLast;
         }
  public long   getStatQueryEdgeDurTotal() {
           return this.statQueryEdgeDurTotal;
         }
  public long   getStatQueryEdgeDurMin() {
           return this.statQueryEdgeDurMin;
         }
  public long   getStatQueryEdgeDurMax() {
           return this.statQueryEdgeDurMax;
         }
  public double getStatQueryEdgeDurAvg() {
           return this.statQueryEdgeDurAvg;
         }
  public int    getStatQueryServerRouteRemainingCount() {
           return this.statQueryServerRouteRemainingCount;
         }
  public long   getStatQueryServerRouteRemainingDurLast() {
           return this.statQueryServerRouteRemainingDurLast;
         }
  public long   getStatQueryServerRouteRemainingDurTotal() {
           return this.statQueryServerRouteRemainingDurTotal;
         }
  public long   getStatQueryServerRouteRemainingDurMin() {
           return this.statQueryServerRouteRemainingDurMin;
         }
  public long   getStatQueryServerRouteRemainingDurMax() {
           return this.statQueryServerRouteRemainingDurMax;
         }
  public double getStatQueryServerRouteRemainingDurAvg() {
           return this.statQueryServerRouteRemainingDurAvg;
         }
  public int    getStatQueryServersLocationsActiveCount() {
           return this.statQueryServersLocationsActiveCount;
         }
  public long   getStatQueryServersLocationsActiveDurLast() {
           return this.statQueryServersLocationsActiveDurLast;
         }
  public long   getStatQueryServersLocationsActiveDurTotal() {
           return this.statQueryServersLocationsActiveDurTotal;
         }
  public long   getStatQueryServersLocationsActiveDurMin() {
           return this.statQueryServersLocationsActiveDurMin;
         }
  public long   getStatQueryServersLocationsActiveDurMax() {
           return this.statQueryServersLocationsActiveDurMax;
         }
  public double getStatQueryServersLocationsActiveDurAvg() {
           return this.statQueryServersLocationsActiveDurAvg;
         }
  public int    getStatQueryUserCount() {
           return this.statQueryUserCount;
         }
  public long   getStatQueryUserDurLast() {
           return this.statQueryUserDurLast;
         }
  public long   getStatQueryUserDurTotal() {
           return this.statQueryUserDurTotal;
         }
  public long   getStatQueryUserDurMin() {
           return this.statQueryUserDurMin;
         }
  public long   getStatQueryUserDurMax() {
           return this.statQueryUserDurMax;
         }
  public double getStatQueryUserDurAvg() {
           return this.statQueryUserDurAvg;
         }
  public int    getStatQueryVertexCount() {
           return this.statQueryVertexCount;
         }
  public long   getStatQueryVertexDurLast() {
           return this.statQueryVertexDurLast;
         }
  public long   getStatQueryVertexDurTotal() {
           return this.statQueryVertexDurTotal;
         }
  public long   getStatQueryVertexDurMin() {
           return this.statQueryVertexDurMin;
         }
  public long   getStatQueryVertexDurMax() {
           return this.statQueryVertexDurMax;
         }
  public double getStatQueryVertexDurAvg() {
           return this.statQueryVertexDurAvg;
         }
  public int[] query(final String sql, final int ncols) throws SQLException {
           return this.storage.DBQuery(sql, ncols);
         }
  public int[] queryEdge(final int v1, final int v2) throws EdgeNotFoundException, SQLException {
           long A0 = System.currentTimeMillis();
           int[] output = this.storage.DBQueryEdge(v1, v2);
               this.statQueryEdgeCount++;
               this.statQueryEdgeDurLast = (System.currentTimeMillis() - A0);
               this.statQueryEdgeDurTotal +=
               this.statQueryEdgeDurLast;
           if (this.statQueryEdgeDurLast <
               this.statQueryEdgeDurMin) {
               this.statQueryEdgeDurMin =
               this.statQueryEdgeDurLast;}
           if (this.statQueryEdgeDurLast >
               this.statQueryEdgeDurMax) {
               this.statQueryEdgeDurMax =
               this.statQueryEdgeDurLast;}
               this.statQueryEdgeDurAvg = (double)
               this.statQueryEdgeDurTotal/
               this.statQueryEdgeCount;
           return output;
         }
  public int[] queryEdgeStatistics() throws SQLException {
           return storage.DBQueryEdgeStatistics();
         }
  public int[] queryEdges() throws SQLException {
           return this.storage.DBQueryEdges();
         }
  public int[] queryEdgesCount() throws SQLException {
           return this.storage.DBQueryEdgesCount();
         }
  public int[] queryMBR() throws SQLException {
           return this.storage.DBQueryMBR();
         }
  public int[] queryMetricRequestDistanceBaseTotal() throws SQLException {
           return storage.DBQueryMetricRequestDistanceBaseTotal();
         }
  public int[] queryMetricRequestDistanceBaseUnassignedTotal() throws SQLException {
           return storage.DBQueryMetricRequestDistanceBaseUnassignedTotal();
         }
  public int[] queryMetricRequestDistanceDetourTotal() throws SQLException {
           return storage.DBQueryMetricRequestDistanceDetourTotal();
         }
  public int[] queryMetricRequestDistanceTransitTotal() throws SQLException {
           return storage.DBQueryMetricRequestDistanceTransitTotal();
         }
  public int[] queryMetricRequestDurationPickupTotal() throws SQLException {
           return storage.DBQueryMetricRequestDurationPickupTotal();
         }
  public int[] queryMetricRequestDurationTransitTotal() throws SQLException {
           return storage.DBQueryMetricRequestDurationTransitTotal();
         }
  public int[] queryMetricRequestDurationTravelTotal() throws SQLException {
           return storage.DBQueryMetricRequestDurationTravelTotal();
         }
  public int[] queryMetricRequestTWViolationsTotal() throws SQLException {
           return storage.DBQueryMetricRequestTWViolationsTotal();
         }
  public int[] queryMetricServerDistanceBaseTotal() throws SQLException {
           return storage.DBQueryMetricServerDistanceBaseTotal();
         }
  public int[] queryMetricServerDistanceCruisingTotal() throws SQLException {
           return storage.DBQueryMetricServerDistanceCruisingTotal();
         }
  public int[] queryMetricServerDistanceServiceTotal() throws SQLException {
           return storage.DBQueryMetricServerDistanceServiceTotal();
         }
  public int[] queryMetricServerDistanceTotal() throws SQLException {
           return storage.DBQueryMetricServerDistanceTotal();
         }
  public int[] queryMetricServerDurationTravelTotal() throws SQLException {
           return storage.DBQueryMetricServerDurationTravelTotal();
         }
  public int[] queryMetricServerTWViolationsTotal() throws SQLException {
           return storage.DBQueryMetricServerTWViolationsTotal();
         }
  public int[] queryMetricServiceRate() throws SQLException {
           return storage.DBQueryMetricServiceRate();
         }
  public int[] queryMetricUserDistanceBaseTotal() throws SQLException {
           return storage.DBQueryMetricUserDistanceBaseTotal();
         }
  public int[] queryRequestTimeOfArrival(final int rid) throws SQLException {
           return storage.DBQueryRequestTimeOfArrival(rid);
         }
  public int[] queryRequestTimeOfDeparture(final int rid) throws SQLException {
           return storage.DBQueryRequestTimeOfDeparture(rid);
         }
  public int[] queryRequestsCount() throws SQLException {
           return storage.DBQueryRequestsCount();
         }
  public int[] queryRequestsQueued(final int t) throws SQLException {
           return storage.DBQueryRequestsQueued(t);
         }
  public int[] queryServerRoute(final int sid) throws SQLException {
           return storage.DBQueryServerRoute(sid);
         }
  public int[] queryServerRouteRemaining(final int sid, final int t) throws SQLException {
           long A0 = System.currentTimeMillis();
           int[] output = this.storage.DBQueryServerRouteRemaining(sid, t);
               this.statQueryServerRouteRemainingCount++;
               this.statQueryServerRouteRemainingDurLast = (System.currentTimeMillis() - A0);
               this.statQueryServerRouteRemainingDurTotal +=
               this.statQueryServerRouteRemainingDurLast;
           if (this.statQueryServerRouteRemainingDurLast <
               this.statQueryServerRouteRemainingDurMin) {
               this.statQueryServerRouteRemainingDurMin =
               this.statQueryServerRouteRemainingDurLast;}
           if (this.statQueryServerRouteRemainingDurLast >
               this.statQueryServerRouteRemainingDurMax) {
               this.statQueryServerRouteRemainingDurMax =
               this.statQueryServerRouteRemainingDurLast;}
               this.statQueryServerRouteRemainingDurAvg = (double)
               this.statQueryServerRouteRemainingDurTotal/
               this.statQueryServerRouteRemainingCount;
           return output;
         }
  public int[] queryServerRouteTraveled(final int sid, final int t, final int n) throws SQLException {
           long A0 = System.currentTimeMillis();
           int[] output = this.storage.DBQueryServerRouteTraveled(sid, t, n);
           return output;
         }
  public int[] queryServerSchedule(final int sid) throws SQLException {
           return storage.DBQueryServerSchedule(sid);
         }
  public int[] queryServerTimeOfDeparture(final int sid) throws SQLException {
           return storage.DBQueryServerTimeOfDeparture(sid);
         }
  public int[] queryServersCount() throws SQLException {
           return storage.DBQueryServersCount();
         }
  public int[] queryServersLocationsActive(final int t) throws SQLException {
           long A0 = System.currentTimeMillis();
           int[] output = this.storage.DBQueryServersLocationsActive(t);
               this.statQueryServersLocationsActiveCount++;
               this.statQueryServersLocationsActiveDurLast = (System.currentTimeMillis() - A0);
               this.statQueryServersLocationsActiveDurTotal +=
               this.statQueryServersLocationsActiveDurLast;
           if (this.statQueryServersLocationsActiveDurLast <
               this.statQueryServersLocationsActiveDurMin) {
               this.statQueryServersLocationsActiveDurMin =
               this.statQueryServersLocationsActiveDurLast;}
           if (this.statQueryServersLocationsActiveDurLast >
               this.statQueryServersLocationsActiveDurMax) {
               this.statQueryServersLocationsActiveDurMax =
               this.statQueryServersLocationsActiveDurLast;}
               this.statQueryServersLocationsActiveDurAvg = (double)
               this.statQueryServersLocationsActiveDurTotal/
               this.statQueryServersLocationsActiveCount;
           return output;
         }
  public int[] queryUser(final int rid) throws UserNotFoundException, SQLException {
           long A0 = System.currentTimeMillis();
           int[] output = storage.DBQueryUser(rid);
               this.statQueryUserCount++;
               this.statQueryUserDurLast = (System.currentTimeMillis() - A0);
               this.statQueryUserDurTotal +=
               this.statQueryUserDurLast;
           if (this.statQueryUserDurLast <
               this.statQueryUserDurMin) {
               this.statQueryUserDurMin =
               this.statQueryUserDurLast;}
           if (this.statQueryUserDurLast >
               this.statQueryUserDurMax) {
               this.statQueryUserDurMax =
               this.statQueryUserDurLast;}
               this.statQueryUserDurAvg = (double)
               this.statQueryUserDurTotal/
               this.statQueryUserCount;
           return output;
         }
  public int[] queryVertex(final int v) throws VertexNotFoundException, SQLException {
           long A0 = System.currentTimeMillis();
           int[] output = this.storage.DBQueryVertex(v);
               this.statQueryVertexCount++;
               this.statQueryVertexDurLast = (System.currentTimeMillis() - A0);
               this.statQueryVertexDurTotal +=
               this.statQueryVertexDurLast;
           if (this.statQueryVertexDurLast <
               this.statQueryVertexDurMin) {
               this.statQueryVertexDurMin =
               this.statQueryVertexDurLast;}
           if (this.statQueryVertexDurLast >
               this.statQueryVertexDurMax) {
               this.statQueryVertexDurMax =
               this.statQueryVertexDurLast;}
               this.statQueryVertexDurAvg = (double)
               this.statQueryVertexDurTotal/
               this.statQueryVertexCount;
           return output;
         }
  public int[] queryVertices() throws SQLException {
           return this.storage.DBQueryVertices();
         }
  public int[] queryVerticesCount() throws SQLException {
           return this.storage.DBQueryVerticesCount();
         }
  public void insertRequest(final int[] u) throws DuplicateUserException, SQLException {
           this.storage.DBInsertRequest(u);
         }
  public void insertServer(final int[] u)
         throws DuplicateUserException, EdgeNotFoundException, SQLException,
                GtreeNotLoadedException, GtreeIllegalSourceException, GtreeIllegalTargetException {
           this.storage.DBInsertServer(u, this.tools.computeRoute(u[4], u[5], u[2]));
         }
  public void loadProblem(String p)
         throws FileNotFoundException, DuplicateUserException, EdgeNotFoundException, SQLException,
                GtreeNotLoadedException, GtreeIllegalSourceException, GtreeIllegalTargetException {
           Scanner sc = new Scanner(new File(p));
           for (int i = 0; i < 6; i++) {
             sc.nextLine();
           }
           while (sc.hasNext()) {
             final int uid = sc.nextInt();
             final int  uo = sc.nextInt();
             final int  ud = sc.nextInt();
             final int  uq = sc.nextInt();
             final int  ue = sc.nextInt();
             final int  ul = sc.nextInt();
             final int  ub = this.tools.computeShortestPathDistance(uo, ud);
             if (uq < 0) {
               this.insertServer(new int[] { uid, uq, ue, ul, uo, ud, ub });
             } else {
               this.insertRequest(new int[] { uid, uq, ue, ul, uo, ud, ub });
             }
           }
         }
  public void loadRoadNetworkFromFile(final String f_rnet) throws FileNotFoundException, SQLException {
           Scanner sc = new Scanner(new File(f_rnet));
           while (sc.hasNext()) {
         final int col0 = sc.nextInt();
         final int col1 = sc.nextInt();
         final int col2 = sc.nextInt();
         final int col3 = (col1 == 0 ? (int) (0*sc.nextDouble()) : (int) Math.round(sc.nextDouble()*CSHIFT));
         final int col4 = (col1 == 0 ? (int) (0*sc.nextDouble()) : (int) Math.round(sc.nextDouble()*CSHIFT));
         final int col5 = (col2 == 0 ? (int) (0*sc.nextDouble()) : (int) Math.round(sc.nextDouble()*CSHIFT));
         final int col6 = (col2 == 0 ? (int) (0*sc.nextDouble()) : (int) Math.round(sc.nextDouble()*CSHIFT));
         try {
           this.storage.DBInsertVertex(col1, col3, col4);
         } catch (DuplicateVertexException e) {
           if (DEBUG) {
             // System.err.println("Warning! Duplicate vertex ignored.");
           }
         }
         try {
           this.storage.DBInsertVertex(col2, col5, col6);
         } catch (DuplicateVertexException e) {
           if (DEBUG) {
             // System.err.println("Warning! Duplicate vertex ignored.");
           }
         }
         final int dist = ((col1 != 0 && col2 != 0)
           ? this.tools.computeHaversine(
                 col3/CSHIFT, col4/CSHIFT,
                 col5/CSHIFT, col6/CSHIFT) : 0);
         try {
           this.storage.DBInsertEdge(col1, col2, dist, 10);
         } catch (DuplicateEdgeException e) {
           if (DEBUG) {
             // System.err.println("Warning! Duplicate edge ignored.");
           }
         }
           }
           this.tools.setRefCacheVertices(this.storage.getRefCacheVertices());
           this.tools.setRefCacheEdges(this.storage.getRefCacheEdges());
         }
  public void cacheRoadNetworkFromDB() throws SQLException {
           this.storage.JargoCacheRoadNetworkFromDB();
         }
  public void cacheUsersFromDB() throws SQLException {
           this.storage.JargoCacheUsersFromDB();
         }
  public void instanceClose() throws SQLException {
           this.storage.JargoInstanceClose();
         }
  public void instanceExport(final String p) throws SQLException {
           this.storage.JargoInstanceExport(p);
         }
  public void instanceInitialize() {
           this.storage.JargoInstanceInitialize();
         }
  public void instanceLoad(final String p) throws SQLException {
           this.storage.JargoInstanceLoad(p);
         }
  public void instanceNew() throws SQLException {
           this.storage.JargoInstanceNew();
         }
  public void gtreeClose() {
           this.tools.GTGtreeClose();
         }
  public void gtreeLoad(String p) throws FileNotFoundException {
           this.tools.GTGtreeLoad(p);
         }
  public int getClockNow() {
           return this.statControllerClockNow;
         }
  public Communicator getRefCommunicator() {
           return this.communicator;
         }
  public Storage getRefStorage() {
           return this.storage;
         }
  public int retrieveQueueSize() {
           return this.client.getStatClientQueueSize();
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
  public void forwardRefCommunicator(final Communicator communicator) {
           this.client.setRefCommunicator(communicator);
         }
  public void forwardRefTraffic(final Traffic traffic) {
           this.communicator.setRefTraffic(traffic);
         }
  public void setClockEnd(final int clock_end) {
           this.CLOCK_END = clock_end;
         }
  public void setClockReference(final String clock_reference) throws IllegalArgumentException {
           int hour = Integer.parseInt(clock_reference.substring(0, 2));
           if (!(0 <= hour && hour <= 23)) {
             throw new IllegalArgumentException("Invalid clock reference (hour got "+hour+"; must be between [00, 23])");
           }
           int minute = Integer.parseInt(clock_reference.substring(2, 4));
           if (!(0 <= minute && minute <= 59)) {
             throw new IllegalArgumentException("Invalid clock reference (minute got "+minute+"; must be between [00, 59])");
           }
           this.statControllerClockReferenceHour= hour;
           this.statControllerClockReferenceMinute = minute;
         }
  public void setClockStart(final int clock_start) {
           this.CLOCK_START = clock_start;
         }
  public void setQueueTimeout(final int queue_timeout) {
           this.QUEUE_TIMEOUT = queue_timeout;
         }
  public void setRefClient(final Client client) {
           this.client = client;
         }
  public final boolean isKilled() {
           return this.kill;
         }
  public void returnRequest(final int[] r) {
           if (this.statControllerClockNow - r[2] < QUEUE_TIMEOUT) {
             this.lu_seen.put(r[0], false);
           }
         }
  public void startRealtime(final Consumer<Boolean> app_cb) {
           this.storage.setRequestTimeout(REQUEST_TIMEOUT);
           this.statControllerClockNow = CLOCK_START;

           int simulation_duration = (CLOCK_END - CLOCK_START);

           this.exe = Executors.newScheduledThreadPool(5);

           this.cb1 = exe.scheduleAtFixedRate(
             this.ClockLoop, 0, 1, TimeUnit.SECONDS);

           this.cb2 = exe.scheduleAtFixedRate(
             this.RequestCollectionLoop, this.loop_delay, REQUEST_COLLECTION_PERIOD, TimeUnit.SECONDS);

           this.cb3 = exe.scheduleAtFixedRate(
             this.RequestHandlingLoop, this.loop_delay, REQUEST_HANDLING_PERIOD, TimeUnit.MILLISECONDS);

           this.cb4 = exe.scheduleAtFixedRate(
             this.ServerLoop, this.loop_delay, SERVER_COLLECTION_PERIOD, TimeUnit.SECONDS);

           this.exe.schedule(() -> {
             this.stop(app_cb);
           }, simulation_duration, TimeUnit.SECONDS);
         }
  public void startSequential(final Consumer<Boolean> app_cb) throws Exception {
           this.storage.setRequestTimeout(REQUEST_TIMEOUT);
           this.statControllerClockNow = CLOCK_START;
           while (!kill && this.statControllerClockNow < CLOCK_END) {
             this.working = true;
             this.ClockLoop.run();  // this.statControllerClockNow gets incremented here!
             this.ServerLoop.run();
             this.RequestCollectionLoop.run();
             this.RequestHandlingLoop.run();
             this.working = false;
           }
           this.stop(app_cb);
         }
  public void stop(final Consumer<Boolean> app_cb) {
           if (this.exe == null) {  // sequential mode
             this.kill = true;
             while (this.working) {
               try {
                 Thread.sleep(100);
               } catch (InterruptedException e) {
                 // ...
               }
             }
           } else {  // realtime mode
             this.cb1.cancel(true);
             this.cb2.cancel(true);
             this.cb3.cancel(true);
             this.cb4.cancel(true);
             this.exe.shutdown();
           }
           try {
             if (this.client != null) {
               this.client.end();
             }
             app_cb.accept(true);
           } catch (Exception e) {
             System.err.println("Error in ending callback");
             System.err.println(e.toString());
             e.printStackTrace();
             return;
           }
         }
}
