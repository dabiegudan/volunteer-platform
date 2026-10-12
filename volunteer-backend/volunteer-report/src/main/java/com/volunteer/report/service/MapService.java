package com.volunteer.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.volunteer.activity.entity.Activity;
import com.volunteer.activity.mapper.ActivityMapper;
import com.volunteer.report.entity.ServiceStation;
import com.volunteer.report.mapper.ServiceStationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * #7 GIS志愿地图与15分钟服务圈 - 服务层
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MapService {

    private final ServiceStationMapper stationMapper;
    private final ActivityMapper activityMapper;

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double SERVICE_CIRCLE_KM = 1.2;

    /**
     * 获取附近的服务站
     */
    public List<Map<String, Object>> getNearbyStations(BigDecimal lat, BigDecimal lng, Double radiusKm) {
        final double radius = (radiusKm != null) ? radiusKm : 5.0;
        List<ServiceStation> all = stationMapper.selectList(
                new LambdaQueryWrapper<ServiceStation>().eq(ServiceStation::getStatus, 1));
        return all.stream()
                .filter(s -> s.getGpsLat() != null && s.getGpsLng() != null)
                .filter(s -> haversineDistance(
                        lat.doubleValue(), lng.doubleValue(),
                        s.getGpsLat().doubleValue(), s.getGpsLng().doubleValue()) <= radius)
                .sorted(Comparator.comparingDouble(s -> haversineDistance(
                        lat.doubleValue(), lng.doubleValue(),
                        s.getGpsLat().doubleValue(), s.getGpsLng().doubleValue())))
                .map(s -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", s.getId());
                    m.put("stationName", s.getStationName());
                    m.put("address", s.getAddress());
                    m.put("gpsLat", s.getGpsLat());
                    m.put("gpsLng", s.getGpsLng());
                    m.put("serviceHours", s.getServiceHours());
                    m.put("contactPhone", s.getContactPhone());
                    m.put("distance", Math.round(haversineDistance(
                            lat.doubleValue(), lng.doubleValue(),
                            s.getGpsLat().doubleValue(), s.getGpsLng().doubleValue()) * 1000.0) / 1000.0);
                    return m;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取15分钟服务圈内的活动(1.2km半径)
     */
    public List<Map<String, Object>> getNearbyActivities(BigDecimal lat, BigDecimal lng) {
        List<Activity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getStatus, "PUBLISHED")
                        .or().eq(Activity::getStatus, "ONGOING")
                        .isNotNull(Activity::getLocationGpsLat)
                        .isNotNull(Activity::getLocationGpsLng));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Activity a : activities) {
            if (a.getLocationGpsLat() == null || a.getLocationGpsLng() == null) continue;
            double distance = haversineDistance(
                    lat.doubleValue(), lng.doubleValue(),
                    a.getLocationGpsLat().doubleValue(), a.getLocationGpsLng().doubleValue());
            if (distance <= SERVICE_CIRCLE_KM) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", a.getId());
                item.put("title", a.getTitle());
                item.put("category", a.getCategory());
                item.put("location", a.getLocation());
                item.put("gpsLat", a.getLocationGpsLat());
                item.put("gpsLng", a.getLocationGpsLng());
                item.put("startTime", a.getStartTime());
                item.put("distance", Math.round(distance * 1000.0) / 1000.0);
                item.put("status", a.getStatus());
                result.add(item);
            }
        }
        result.sort(Comparator.comparingDouble(m -> (Double) m.get("distance")));
        return result;
    }

    /**
     * 获取所有地图点位(活动+服务站)
     */
    public List<Map<String, Object>> getAllMapPoints(BigDecimal lat, BigDecimal lng, Double radiusKm) {
        if (radiusKm == null) radiusKm = 10.0;
        List<Map<String, Object>> points = new ArrayList<>();

        List<ServiceStation> stations = stationMapper.selectList(
                new LambdaQueryWrapper<ServiceStation>().eq(ServiceStation::getStatus, 1));
        for (ServiceStation s : stations) {
            double distance = haversineDistance(
                    lat.doubleValue(), lng.doubleValue(),
                    s.getGpsLat().doubleValue(), s.getGpsLng().doubleValue());
            if (distance <= radiusKm) {
                Map<String, Object> point = new HashMap<>();
                point.put("id", s.getId());
                point.put("type", "station");
                point.put("name", s.getStationName());
                point.put("address", s.getAddress());
                point.put("gpsLat", s.getGpsLat());
                point.put("gpsLng", s.getGpsLng());
                point.put("distance", Math.round(distance * 1000.0) / 1000.0);
                point.put("phone", s.getContactPhone());
                points.add(point);
            }
        }

        List<Activity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>()
                        .in(Activity::getStatus, Arrays.asList("PUBLISHED", "ONGOING"))
                        .isNotNull(Activity::getLocationGpsLat)
                        .isNotNull(Activity::getLocationGpsLng));
        for (Activity a : activities) {
            if (a.getLocationGpsLat() == null || a.getLocationGpsLng() == null) continue;
            double distance = haversineDistance(
                    lat.doubleValue(), lng.doubleValue(),
                    a.getLocationGpsLat().doubleValue(), a.getLocationGpsLng().doubleValue());
            if (distance <= radiusKm) {
                Map<String, Object> point = new HashMap<>();
                point.put("id", a.getId());
                point.put("type", "activity");
                point.put("name", a.getTitle());
                point.put("location", a.getLocation());
                point.put("gpsLat", a.getLocationGpsLat());
                point.put("gpsLng", a.getLocationGpsLng());
                point.put("distance", Math.round(distance * 1000.0) / 1000.0);
                point.put("category", a.getCategory());
                points.add(point);
            }
        }
        points.sort(Comparator.comparingDouble(m -> (Double) m.get("distance")));
        return points;
    }

    private double haversineDistance(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}