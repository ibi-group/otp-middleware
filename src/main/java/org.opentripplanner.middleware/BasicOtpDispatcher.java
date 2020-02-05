package org.opentripplanner.middleware;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

public class BasicOtpDispatcher {

    /**
     * @return Example response:
     * [Response 0 received in 844 ms.,
     * Response 1 received in 780 ms.,
     * Response 2 received in 853 ms.,
     * Response 3 received in 1433 ms.,
     * Response 4 received in 1791 ms.,
     * Response 5 received in 660 ms.,
     * Response 6 received in 180 ms.,
     * Response 7 received in 135 ms.,
     * Response 8 received in 154 ms.]
     * Completed in 7149 ms.
     */
    public static String executeRequestsInSequence() {
        long[] times = new long[Const.urls.length];
        long startTime = System.currentTimeMillis();

        StringBuilder sb = new StringBuilder();
        sb.append("[");

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {

            for (int i = 0; i < Const.urls.length; i++) {
                HttpGet httpget = new HttpGet(Const.urls[i]);
                httpget.addHeader("Connection", "Keep-Alive");
                httpget.addHeader("Keep-Alive", "timeout=5, max=1000");

                System.out.println("----------------------------------------");
                System.out.println("Executing request " + i + " " + httpget.getRequestLine());

                // Create a custom response handler
                int finalI = i;
                times[i] = System.currentTimeMillis();

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
                // This call is blocking.
                String responseBody = httpclient.execute(httpget, responseHandler);

                String log = "Response " + i + " received in " + times[i] + " ms.";
                System.out.println(log);

                if (i != 0) sb.append(",<br/>");

                // System.out.println(responseBody.substring(0, Math.min(1000, responseBody.length())));
                // sb.append(responseBody);
                sb.append(log);
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        sb
        .append("]")
        .append("<br/>")
        .append("Completed in ")
        .append(System.currentTimeMillis() - startTime)
        .append(" ms.");

        return sb.toString();
    }
}
