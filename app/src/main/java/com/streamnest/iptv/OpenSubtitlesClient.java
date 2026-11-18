package com.streamnest.iptv;

import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class OpenSubtitlesClient {
    private static final String API =
            "https://api.opensubtitles.com/api/v1";

    private OpenSubtitlesClient() {}

    public static JSONObject login(
            String apiKey, String username, String password)
            throws Exception {
        HttpURLConnection connection = (HttpURLConnection)
                new URL(API + "/login").openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Api-Key", apiKey);
            connection.setRequestProperty("User-Agent",
                    "StreamNestIPTV v1.0");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type",
                    "application/json");

            JSONObject body = new JSONObject();
            body.put("username", username);
            body.put("password", password);

            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException(
                        "OpenSubtitles login failed (HTTP " + status + ")");
            }

            try (InputStream in = connection.getInputStream();
                 ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    bytes.write(buffer, 0, count);
                }
                JSONObject result = new JSONObject(
                        bytes.toString(StandardCharsets.UTF_8));
                if (result.optString("token", "").isEmpty()) {
                    throw new IOException(
                            "OpenSubtitles did not return a login token");
                }
                return result;
            }
        } finally {
            connection.disconnect();
        }
    }
}
