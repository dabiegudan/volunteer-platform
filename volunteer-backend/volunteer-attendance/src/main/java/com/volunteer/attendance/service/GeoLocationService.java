package com.volunteer.attendance.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GeoLocationService {
    
    private static final double EARTH_RADIUS = 6371.0;
    
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS * c * 1000;
    }
    
    public double calculateDistanceFromGps(String gps1, String gps2) {
        try {
            double[] coords1 = parseGps(gps1);
            double[] coords2 = parseGps(gps2);
            return calculateDistance(coords1[0], coords1[1], coords2[0], coords2[1]);
        } catch (Exception e) {
            log.warn("Failed to calculate distance from GPS: {} and {}", gps1, gps2, e);
            return -1;
        }
    }
    
    public boolean isWithinRange(String userGps, String targetGps, double maxDistanceMeters) {
        double distance = calculateDistanceFromGps(userGps, targetGps);
        return distance >= 0 && distance <= maxDistanceMeters;
    }
    
    public double[] parseGps(String gps) {
        if (gps == null || gps.trim().isEmpty()) {
            throw new IllegalArgumentException("GPS coordinates cannot be empty");
        }
        
        String[] parts = gps.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid GPS format, expected: lat,lon");
        }
        
        try {
            double lat = Double.parseDouble(parts[0].trim());
            double lon = Double.parseDouble(parts[1].trim());
            return new double[]{lat, lon};
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid GPS coordinates", e);
        }
    }
    
    public String formatGps(double lat, double lon) {
        return String.format("%.6f,%.6f", lat, lon);
    }
}
