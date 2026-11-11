package com.bri.model;

/** 一带一路国家（含经纬度），对应 countries.json */
public class BriCountry {
    private String code;
    private String name;
    private String nameEn;
    private String region;
    private double latitude;
    private double longitude;

    public BriCountry() {}

    public BriCountry(String code, String name, String nameEn, String region,
                      double lat, double lon) {
        this.code = code; this.name = name; this.nameEn = nameEn;
        this.region = region; this.latitude = lat; this.longitude = lon;
    }

    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String v) { this.nameEn = v; }
    public String getRegion() { return region; }
    public void setRegion(String v) { this.region = v; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double v) { this.latitude = v; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double v) { this.longitude = v; }
}
