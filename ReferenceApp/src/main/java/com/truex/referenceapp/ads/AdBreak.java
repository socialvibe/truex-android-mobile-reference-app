package com.truex.referenceapp.ads;

import org.apache.commons.text.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AdBreak {
    public String id;
    public int timeOffsetMs;
    public int duration;
    public boolean viewed = false;
    public List<String> adUrls = new ArrayList<>();

    public void parseJson(JSONObject adBreak) {
        try {
            id = adBreak.getString("breakId");
            timeOffsetMs = adBreak.getInt("timeOffsetMs");
            duration = adBreak.getInt("videoAdDuration");

            JSONArray ads = adBreak.getJSONArray("ads");
            for (int i = 0; i < ads.length(); i++) {
                JSONObject ad = ads.getJSONObject(i);
                String adUrl = StringEscapeUtils.unescapeJava(ad.getString("adUrl"));
                adUrls.add(adUrl);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public String getFirstAd() {
        return adUrls != null && !adUrls.isEmpty() ? adUrls.get(0) : null;
    }
}
