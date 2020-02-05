package org.opentripplanner.middleware;

import java.io.IOException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import static spark.Spark.*;

class Main {
    // Play with some HTTP requests
    static final int numItineraries = 3;
    static final String[] urls = new String[] {
            // Each request made individually below from OTP MOD UI
            // is in reality two requests, one with and one without realtime updates.

            "https://maps.trimet.org/otp_mod/plan?fromPlace=1610%20SW%20Clifton%20St%2C%20Portland%2C%20OR%2C%20USA%2097201%3A%3A45.51091832390635%2C-122.69433801297359&toPlace=3335%20SE%2010th%20Ave%2C%20Portland%2C%20OR%2C%20USA%2097202%3A%3A45.49912810913339%2C-122.656202229323&mode=WALK%2CBUS%2CTRAM%2CRAIL%2CGONDOLA&showIntermediateStops=true&maxWalkDistance=1207&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&numItineraries=" + numItineraries,
            "https://maps.trimet.org/otp_mod/plan?fromPlace=1610%20SW%20Clifton%20St%2C%20Portland%2C%20OR%2C%20USA%2097201%3A%3A45.51091832390635%2C-122.69433801297359&toPlace=3335%20SE%2010th%20Ave%2C%20Portland%2C%20OR%2C%20USA%2097202%3A%3A45.49912810913339%2C-122.656202229323&mode=BUS%2CTRAM%2CRAIL%2CGONDOLA%2CBICYCLE&showIntermediateStops=true&maxWalkDistance=4828&maxBikeDistance=4828&optimize=SAFE&bikeSpeed=3.58&ignoreRealtimeUpdates=true&numItineraries=" + numItineraries,
            "https://maps.trimet.org/otp_mod/plan?fromPlace=1610%20SW%20Clifton%20St%2C%20Portland%2C%20OR%2C%20USA%2097201%3A%3A45.51091832390635%2C-122.69433801297359&toPlace=3335%20SE%2010th%20Ave%2C%20Portland%2C%20OR%2C%20USA%2097202%3A%3A45.49912810913339%2C-122.656202229323&mode=BUS%2CTRAM%2CRAIL%2CGONDOLA%2CBICYCLE_RENT&showIntermediateStops=true&maxWalkDistance=4828&maxBikeDistance=4828&optimize=SAFE&bikeSpeed=3.58&ignoreRealtimeUpdates=true&companies=BIKETOWN&numItineraries=" + numItineraries,
            "https://maps.trimet.org/otp_mod/plan?fromPlace=1610%20SW%20Clifton%20St%2C%20Portland%2C%20OR%2C%20USA%2097201%3A%3A45.51091832390635%2C-122.69433801297359&toPlace=3335%20SE%2010th%20Ave%2C%20Portland%2C%20OR%2C%20USA%2097202%3A%3A45.49912810913339%2C-122.656202229323&mode=BUS%2CTRAM%2CRAIL%2CGONDOLA%2CMICROMOBILITY_RENT&showIntermediateStops=true&optimize=QUICK&maxWalkDistance=4828&maxEScooterDistance=4828&ignoreRealtimeUpdates=true&companies=BIRD%2CLIME%2CRAZOR%2CSHARED%2CSPIN&numItineraries=" + numItineraries,
            "https://maps.trimet.org/otp_mod/plan?fromPlace=1610%20SW%20Clifton%20St%2C%20Portland%2C%20OR%2C%20USA%2097201%3A%3A45.51091832390635%2C-122.69433801297359&toPlace=3335%20SE%2010th%20Ave%2C%20Portland%2C%20OR%2C%20USA%2097202%3A%3A45.49912810913339%2C-122.656202229323&mode=BUS%2CTRAM%2CRAIL%2CGONDOLA%2CCAR_PARK%2CWALK&showIntermediateStops=true&optimize=QUICK&ignoreRealtimeUpdates=true&numItineraries=" + numItineraries,
            "https://maps.trimet.org/otp_mod/plan?fromPlace=1610%20SW%20Clifton%20St%2C%20Portland%2C%20OR%2C%20USA%2097201%3A%3A45.51091832390635%2C-122.69433801297359&toPlace=3335%20SE%2010th%20Ave%2C%20Portland%2C%20OR%2C%20USA%2097202%3A%3A45.49912810913339%2C-122.656202229323&mode=BUS%2CTRAM%2CRAIL%2CGONDOLA%2CCAR_HAIL%2CWALK&showIntermediateStops=true&optimize=QUICK&ignoreRealtimeUpdates=true&companies=UBER&minTransitDistance=50%25&searchTimeout=10000&numItineraries=" + numItineraries,
            "https://maps.trimet.org/otp_mod/plan?fromPlace=1610%20SW%20Clifton%20St%2C%20Portland%2C%20OR%2C%20USA%2097201%3A%3A45.51091832390635%2C-122.69433801297359&toPlace=3335%20SE%2010th%20Ave%2C%20Portland%2C%20OR%2C%20USA%2097202%3A%3A45.49912810913339%2C-122.656202229323&mode=WALK&showIntermediateStops=true&walkSpeed=1.34&ignoreRealtimeUpdates=true&companies=UBER&numItineraries=" + numItineraries,
            "https://maps.trimet.org/otp_mod/plan?fromPlace=1610%20SW%20Clifton%20St%2C%20Portland%2C%20OR%2C%20USA%2097201%3A%3A45.51091832390635%2C-122.69433801297359&toPlace=3335%20SE%2010th%20Ave%2C%20Portland%2C%20OR%2C%20USA%2097202%3A%3A45.49912810913339%2C-122.656202229323&mode=BICYCLE&showIntermediateStops=true&optimize=SAFE&bikeSpeed=3.58&ignoreRealtimeUpdates=true&companies=UBER&numItineraries=" + numItineraries,
            "https://maps.trimet.org/otp_mod/plan?fromPlace=1610%20SW%20Clifton%20St%2C%20Portland%2C%20OR%2C%20USA%2097201%3A%3A45.51091832390635%2C-122.69433801297359&toPlace=3335%20SE%2010th%20Ave%2C%20Portland%2C%20OR%2C%20USA%2097202%3A%3A45.49912810913339%2C-122.656202229323&mode=BICYCLE_RENT&showIntermediateStops=true&optimize=SAFE&bikeSpeed=3.58&ignoreRealtimeUpdates=true&companies=UBER&numItineraries=" + numItineraries,
    };

    public static void main(String[] args) {
        // Define some endpoints,
        // available at http://localhost:4567/hello
        get("/hello", (req, res) -> executeRequestsInSequence());
    }

    private static String executeRequestsInSequence() {
        long[] times = new long[urls.length];
        long startTime = System.currentTimeMillis();

        StringBuilder sb = new StringBuilder();
        sb.append("[");

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {

            for (int i = 0; i < urls.length; i++) {
                HttpGet httpget = new HttpGet(urls[i]);
                httpget.addHeader("Connection", "Keep-Alive");
                httpget.addHeader("Keep-Alive", "timeout=5, max=1000");

                System.out.println("----------------------------------------");
                System.out.println("Executing request " + i + " " + httpget.getRequestLine());

                // Create a custom response handler
                int finalI = i;
                ResponseHandler<String> responseHandler = new ResponseHandler<String>() {

                    @Override
                    public String handleResponse(
                            final HttpResponse response) throws ClientProtocolException, IOException {
                        int status = response.getStatusLine().getStatusCode();
                        if (status >= 200 && status < 300) {
                            times[finalI] = System.currentTimeMillis() - times[finalI];
                            HttpEntity entity = response.getEntity();
                            return entity != null ? EntityUtils.toString(entity) : null;
                        } else {
                            throw new ClientProtocolException("Unexpected response status: " + status);
                        }
                    }

                };
                times[i] = System.currentTimeMillis();

                String responseBody = httpclient.execute(httpget, responseHandler);

                System.out.println("Response " + i + " received in " + times[i] + " ms.");
                // System.out.println(responseBody.substring(0, Math.min(1000, responseBody.length())));

                if (i != 0) sb.append(",<br/>");
                // sb.append(responseBody);
                sb.append("Response " + i + " received in " + times[i] + " ms.");
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        sb.append("]");
        sb.append("<br/>");
        sb.append("Completed in " + (System.currentTimeMillis() - startTime) + " ms.");

        return sb.toString();

    }
}

