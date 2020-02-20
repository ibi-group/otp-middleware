package org.opentripplanner.middleware;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.opentripplanner.middleware.Const.*;

public class OtpJMeterRequestGenerator {

    final static int NUM_ITINERARIES = 3;

    public static void main(String[] args) {
        // Just print out a list of OTP requests from the places in the const file.
        // Combine the following modes: Walk, Transit, Bike, Bikeshare

        //System.out.println("<h1>JMeter Requests</h1>");
        //System.out.println("<p>Structure: One thread group = One row in table above, each group runs the sequence of OD pairs, each pair runs the modes in parallel.</p>");
        //System.out.println("<pre>");


        // JMeter stuff
        System.out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        System.out.println("<jmeterTestPlan version=\"1.2\" properties=\"5.0\" jmeter=\"5.2.1\">");
        System.out.println("<hashTree>");
        System.out.println("<TestPlan guiclass=\"TestPlanGui\" testclass=\"TestPlan\" testname=\"Multiple OTP HTTP Requests\" enabled=\"true\">");
        System.out.println("<stringProp name=\"TestPlan.comments\"></stringProp>");
        System.out.println("<boolProp name=\"TestPlan.functional_mode\">false</boolProp>");
        System.out.println("<boolProp name=\"TestPlan.tearDown_on_shutdown\">true</boolProp>");
        System.out.println("<boolProp name=\"TestPlan.serialize_threadgroups\">false</boolProp>");
        System.out.println("<elementProp name=\"TestPlan.user_defined_variables\" elementType=\"Arguments\" guiclass=\"ArgumentsPanel\" testclass=\"Arguments\" testname=\"User Defined Variables\" enabled=\"true\">");
        System.out.println("<collectionProp name=\"Arguments.arguments\">");
        System.out.println("<elementProp name=\"numItineraries\" elementType=\"Argument\">");
        System.out.println("<stringProp name=\"Argument.name\">numItineraries</stringProp>");
        System.out.println("<stringProp name=\"Argument.value\">3</stringProp>");
        System.out.println("<stringProp name=\"Argument.metadata\">=</stringProp>");
        System.out.println("</elementProp>");
        System.out.println("</collectionProp>");
        System.out.println("</elementProp>");
        System.out.println("<stringProp name=\"TestPlan.user_define_classpath\"></stringProp>");
        System.out.println("</TestPlan>");
        System.out.println("<hashTree>");

        // Start thread group here - one per row.

        for (int i = 0; i < locations.size(); i++) {
            Map.Entry<String, Double[]> e1 = locations.get(i);

            System.out.println("<ThreadGroup guiclass=\"ThreadGroupGui\" testclass=\"ThreadGroup\" testname=\"User " + (i+1) + " at " + e1.getKey() + "\" enabled=\"true\">");
            System.out.println("<stringProp name=\"ThreadGroup.on_sample_error\">continue</stringProp>");
            System.out.println("<elementProp name=\"ThreadGroup.main_controller\" elementType=\"LoopController\" guiclass=\"LoopControlPanel\" testclass=\"LoopController\" testname=\"Loop Controller\" enabled=\"true\">");
            System.out.println("<boolProp name=\"LoopController.continue_forever\">false</boolProp>");
            System.out.println("<stringProp name=\"LoopController.loops\">1</stringProp>");
            System.out.println("</elementProp>");
            System.out.println("<stringProp name=\"ThreadGroup.num_threads\">1</stringProp>");
            System.out.println("<stringProp name=\"ThreadGroup.ramp_time\">0</stringProp>");
            System.out.println("<boolProp name=\"ThreadGroup.scheduler\">false</boolProp>");
            System.out.println("<stringProp name=\"ThreadGroup.duration\"></stringProp>");
            System.out.println("<stringProp name=\"ThreadGroup.delay\"></stringProp>");
            System.out.println("<boolProp name=\"ThreadGroup.same_user_on_next_iteration\">true</boolProp>");
            System.out.println("</ThreadGroup>");
            System.out.println();

            System.out.println("<hashTree>");

            for (int j = 0; j < locations.size(); j++) {
                if (i != j) {
                    String indexStr = i + "_" + j + "_";
                    Map.Entry<String, Double[]> e2 = locations.get(j);

                    System.out.println("<com.blazemeter.jmeter.http.ParallelHTTPSampler guiclass=\"com.blazemeter.jmeter.http.ParallelHTTPSamplerGui\" testclass=\"com.blazemeter.jmeter.http.ParallelHTTPSampler\" testname=\"User " + (i+1) + " going to " + e2.getKey() + " (parallel reqsts)\" enabled=\"true\">");
                    System.out.println("<elementProp name=\"HTTPsampler.Arguments\" elementType=\"Arguments\">");
                    System.out.println("<collectionProp name=\"Arguments.arguments\"/>");
                    System.out.println("</elementProp>");
                    System.out.println("<boolProp name=\"HTTPSampler.image_parser\">true</boolProp>");
                    System.out.println("<boolProp name=\"HTTPSampler.concurrentDwn\">true</boolProp>");
                    System.out.println("<collectionProp name=\"urls\">");


                    for (int k = 0; k < modeParams.size(); k++) {
                        Map.Entry<String, String> m = modeParams.get(k);
                        String planUrl = makeOtpRequestUrl(OTP_PLAN_URL, e1, e2, m.getValue());
                        System.out.println("<collectionProp name=\"req_" + indexStr + k + "\">");
                        System.out.println("<stringProp name=\"url_" + indexStr + k + "\">" + planUrl + "</stringProp>");
                        System.out.println("</collectionProp>");
                    }

                    System.out.println("</collectionProp>");
                    System.out.println("</com.blazemeter.jmeter.http.ParallelHTTPSampler>");
                    System.out.println("<hashTree/>");
                }
            }

            System.out.println("</hashTree>");
            System.out.println();
        }


        System.out.println("<ResultCollector guiclass=\"SummaryReport\" testclass=\"ResultCollector\" testname=\"Summary Report\" enabled=\"true\">");
        System.out.println("<boolProp name=\"ResultCollector.error_logging\">false</boolProp>");
        System.out.println("<objProp>");
        System.out.println("<name>saveConfig</name>");
        System.out.println("<value class=\"SampleSaveConfiguration\">");
        System.out.println("<time>true</time>");
        System.out.println("<latency>true</latency>");
        System.out.println("<timestamp>true</timestamp>");
        System.out.println("<success>true</success>");
        System.out.println("<label>true</label>");
        System.out.println("<code>true</code>");
        System.out.println("<message>true</message>");
        System.out.println("<threadName>true</threadName>");
        System.out.println("<dataType>true</dataType>");
        System.out.println("<encoding>false</encoding>");
        System.out.println("<assertions>true</assertions>");
        System.out.println("<subresults>true</subresults>");
        System.out.println("<responseData>false</responseData>");
        System.out.println("<samplerData>false</samplerData>");
        System.out.println("<xml>false</xml>");
        System.out.println("<fieldNames>true</fieldNames>");
        System.out.println("<responseHeaders>false</responseHeaders>");
        System.out.println("<requestHeaders>false</requestHeaders>");
        System.out.println("<responseDataOnError>false</responseDataOnError>");
        System.out.println("<saveAssertionResultsFailureMessage>true</saveAssertionResultsFailureMessage>");
        System.out.println("<assertionsResultsToSave>0</assertionsResultsToSave>");
        System.out.println("<bytes>true</bytes>");
        System.out.println("<sentBytes>true</sentBytes>");
        System.out.println("<url>true</url>");
        System.out.println("<threadCounts>true</threadCounts>");
        System.out.println("<idleTime>true</idleTime>");
        System.out.println("<connectTime>true</connectTime>");
        System.out.println("</value>");
        System.out.println("</objProp>");
        System.out.println("<stringProp name=\"filename\">combinations.csv</stringProp>");
        System.out.println("</ResultCollector>");
        System.out.println("<hashTree/>");
        System.out.println("<ResultCollector guiclass=\"ViewResultsFullVisualizer\" testclass=\"ResultCollector\" testname=\"View Results Tree\" enabled=\"true\">");
        System.out.println("<boolProp name=\"ResultCollector.error_logging\">false</boolProp>");
        System.out.println("<objProp>");
        System.out.println("<name>saveConfig</name>");
        System.out.println("<value class=\"SampleSaveConfiguration\">");
        System.out.println("<time>true</time>");
        System.out.println("<latency>true</latency>");
        System.out.println("<timestamp>true</timestamp>");
        System.out.println("<success>true</success>");
        System.out.println("<label>true</label>");
        System.out.println("<code>true</code>");
        System.out.println("<message>true</message>");
        System.out.println("<threadName>true</threadName>");
        System.out.println("<dataType>true</dataType>");
        System.out.println("<encoding>false</encoding>");
        System.out.println("<assertions>true</assertions>");
        System.out.println("<subresults>true</subresults>");
        System.out.println("<responseData>false</responseData>");
        System.out.println("<samplerData>false</samplerData>");
        System.out.println("<xml>false</xml>");
        System.out.println("<fieldNames>true</fieldNames>");
        System.out.println("<responseHeaders>false</responseHeaders>");
        System.out.println("<requestHeaders>false</requestHeaders>");
        System.out.println("<responseDataOnError>false</responseDataOnError>");
        System.out.println("<saveAssertionResultsFailureMessage>true</saveAssertionResultsFailureMessage>");
        System.out.println("<assertionsResultsToSave>0</assertionsResultsToSave>");
        System.out.println("<bytes>true</bytes>");
        System.out.println("<sentBytes>true</sentBytes>");
        System.out.println("<url>true</url>");
        System.out.println("<threadCounts>true</threadCounts>");
        System.out.println("<idleTime>true</idleTime>");
        System.out.println("<connectTime>true</connectTime>");
        System.out.println("</value>");
        System.out.println("</objProp>");
        System.out.println("<stringProp name=\"filename\"></stringProp>");
        System.out.println("</ResultCollector>");
        System.out.println("<hashTree/>");


        System.out.println("</hashTree>");
        System.out.println("</hashTree>");
        System.out.println("</jmeterTestPlan>");
    }

    public static String getPlaceParam(Map.Entry<String, Double[]> loc) {
        Double[] arr = loc.getValue();
        double lat = arr[1];
        double lon = arr[0];

        return URLEncoder.encode(loc.getKey() + "::" + lat + "," + lon, StandardCharsets.UTF_8);
    }

    public static String makeOtpRequestUrl(String baseUrl, Map.Entry<String, Double[]> loc1, Map.Entry<String, Double[]> loc2, String modeParam) {
        String result = baseUrl
                + "?fromPlace=" + getPlaceParam(loc1)
                + "&toPlace=" + getPlaceParam(loc2)
                + "&mode=" + modeParam
                + "&showIntermediateStops=true&maxWalkDistance=1609&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true"
                + "&numItineraries=" + NUM_ITINERARIES;

        return result.replaceAll("&", "&amp;");
    }
}
