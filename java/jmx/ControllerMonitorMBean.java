package com.github.jargors.jmx;
import com.github.jargors.Controller;
public interface ControllerMonitorMBean {
  public int  getStatControllerClock();
  public int  getStatControllerClockReferenceDay();
  public int  getStatControllerClockReferenceHour();
  public int  getStatControllerClockReferenceMinute();
  public int  getStatControllerClockReferenceSecond();
  public int  getStatControllerRequestCollectionSize();
  public long getStatControllerRequestCollectionDur();
  public long getStatQueryDur();
  public long getStatQueryEdgeDur();
  public long getStatQueryEdgeStatisticsDur();
  public long getStatQueryEdgesCountDur();
  public long getStatQueryEdgesDur();
  public long getStatQueryMBRDur();
  public long getStatQueryMetricRequestDistanceBaseTotalDur();
  public long getStatQueryMetricRequestDistanceBaseUnassignedTotalDur();
  public long getStatQueryMetricRequestDistanceDetourTotalDur();
  public long getStatQueryMetricRequestDistanceTransitTotalDur();
  public long getStatQueryMetricRequestDurationPickupTotalDur();
  public long getStatQueryMetricRequestDurationTransitTotalDur();
  public long getStatQueryMetricRequestDurationTravelTotalDur();
  public long getStatQueryMetricRequestTWViolationsTotalDur();
  public long getStatQueryMetricServerDistanceBaseTotalDur();
  public long getStatQueryMetricServerDistanceCruisingTotalDur();
  public long getStatQueryMetricServerDistanceServiceTotalDur();
  public long getStatQueryMetricServerDistanceTotalDur();
  public long getStatQueryMetricServerDurationTravelTotalDur();
  public long getStatQueryMetricServerTWViolationsTotalDur();
  public long getStatQueryMetricServiceRateDur();
  public long getStatQueryMetricUserDistanceBaseTotalDur();
  public long getStatQueryRequestTimeOfArrivalDur();
  public long getStatQueryRequestTimeOfDepartureDur();
  public long getStatQueryRequestsCountDur();
  public long getStatQueryRequestsQueuedDur();
  public long getStatQueryServerRouteActiveDur();
  public long getStatQueryServerRouteDur();
  public long getStatQueryServerRouteRemainingDur();
  public long getStatQueryServerScheduleDur();
  public long getStatQueryServerTimeOfDepartureDur();
  public long getStatQueryServersActiveDur();
  public long getStatQueryServersCountDur();
  public long getStatQueryServersLocationsActiveDur();
  public long getStatQueryUserDur();
  public long getStatQueryVertexDur();
  public long getStatQueryVerticesCountDur();
  public long getStatQueryVerticesDur();
}
