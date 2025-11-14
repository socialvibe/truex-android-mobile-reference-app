package com.truex.referenceapp.ads;

import android.content.Context;
import android.util.Log;

import org.apache.commons.text.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Provides ad break data by parsing JSON configuration from resources.
 */
public class AdProvider {
    private static final String CLASSTAG = AdProvider.class.getSimpleName();

    private List<AdBreak> adBreakList;

    public AdProvider(Context context, int resourceId) {
        this.adBreakList = new ArrayList<>();
        parseAdBreaks(context, resourceId);
    }

    /**
     * Get all ad breaks as a list
     * Returns ad breaks sorted by timeOffset
     */
    public List<AdBreak> getAllAdBreaks() {
        return new ArrayList<>(adBreakList);
    }

    private void parseAdBreaks(Context context, int resourceId) {
        String rawFile = getRawFileContents(context, resourceId);
        try {
            JSONObject rawJson = new JSONObject(rawFile);
            JSONArray adBreaksArray = rawJson.getJSONArray("adBreaks");

            for (int i = 0; i < adBreaksArray.length(); i++) {
                JSONObject adBreakJson = adBreaksArray.getJSONObject(i);

                String breakId = adBreakJson.getString("breakId");
                int timeOffsetMs = adBreakJson.getInt("timeOffsetMs");
                int duration = adBreakJson.optInt("videoAdDuration", 30);

                List<Ad> ads = new ArrayList<>();
                JSONArray adsArray = adBreakJson.getJSONArray("ads");

                for (int j = 0; j < adsArray.length(); j++) {
                    JSONObject adJson = adsArray.getJSONObject(j);

                    String adId = adJson.optString("id", "ad-" + j);
                    String adSystem = adJson.optString("adSystem", "GDFP");

                    String mediaFile = null;
                    if (adJson.has("mediaFile")) {
                        mediaFile = StringEscapeUtils.unescapeJava(adJson.getString("mediaFile"));
                    }

                    String description = null;
                    if (adJson.has("description")) {
                        description = StringEscapeUtils.unescapeJava(adJson.getString("description"));
                    }

                    JSONObject adParameters = null;
                    if (adJson.has("adParameters")) {
                        adParameters = adJson.getJSONObject("adParameters");
                    }

                    int adDuration = adJson.optInt("duration", duration);

                    Ad ad = new Ad(adSystem, mediaFile, description, adParameters, adDuration, adId);
                    ads.add(ad);
                }

                // Create AdBreak object
                AdBreak adBreak = new AdBreak(breakId, timeOffsetMs, ads);
                adBreakList.add(adBreak);
            }

            // Sort ad breaks by time offset
            Collections.sort(adBreakList, new Comparator<AdBreak>() {
                @Override
                public int compare(AdBreak a, AdBreak b) {
                    return Integer.compare(a.getTimeOffsetMs(), b.getTimeOffsetMs());
                }
            });

            Log.d(CLASSTAG, "Parsed " + adBreakList.size() + " ad breaks");
        } catch (JSONException e) {
            Log.e(CLASSTAG, "Error parsing ad breaks JSON", e);
        }
    }

    private String getRawFileContents(Context context, int resourceId) {
        InputStream stream = context.getResources().openRawResource(resourceId);
        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(stream));
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
        } catch (IOException e) {
            Log.e(CLASSTAG, "Error reading raw file", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(CLASSTAG, "Error closing reader", e);
                }
            }
        }
        return stringBuilder.toString();
    }
}
