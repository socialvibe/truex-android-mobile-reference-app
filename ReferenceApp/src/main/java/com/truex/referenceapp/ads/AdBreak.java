package com.truex.referenceapp.ads;

import org.apache.commons.text.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AdBreak {
    public String id;
    public long contentPositionMs;
    public int duration;
    public boolean viewed = false;
    public List<String> adUrls;
    public List<Integer> adDurations;

    static public AdBreak fromJson(JSONObject json) throws JSONException {
        AdBreak result = new AdBreak();
        result.id = json.getString("breakId");
        result.contentPositionMs = parsePosition(json.getString("contentPosition"));
        result.duration = 0;
        result.adUrls = new ArrayList<>();
        result.adDurations = new ArrayList<>();

        JSONArray ads = json.getJSONArray("ads");
        for (int i = 0; i < ads.length(); i++) {
            JSONObject ad = ads.getJSONObject(i);
            String adUrl = StringEscapeUtils.unescapeJava(ad.getString("adUrl"));
            result.adUrls.add(adUrl);

            int adDuration = ad.getInt("duration");
            result.adDurations.add(adDuration);

            result.duration += adDuration;
        }
        return result;
    }

    static public long parsePosition(String hhmmss) throws JSONException {
        if (hhmmss == null) throw new JSONException("missing contentPosition");
        String[] parts = hhmmss.split(":");
        if (parts.length < 1 || parts.length > 3) throw new JSONException("invalid contentPosition: " + hhmmss);
        int seconds = 0;
        for(int i = parts.length-1; i >= 0; i--) {
            int timeValue = Integer.parseInt(parts[i]);
            seconds = seconds * 60 + timeValue;
        }
        return (long) seconds * 1000;
    }

    public String getFirstAd() {
        return adUrls != null && !adUrls.isEmpty() ? adUrls.get(0) : null;
    }
}
