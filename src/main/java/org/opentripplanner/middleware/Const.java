package org.opentripplanner.middleware;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Const {
    // Play with some HTTP requests
    public static final int numItineraries = 3;
    public static final String[] urls = new String[] {
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

    public static final String OTP_PLAN_URL = "https://fdot-otp-server.ibi-transit.com/otp/routers/default/plan";
    public static final String OTP_UI_URL = "https://fdot-otp.ibi-transit.com/#/";

    // Orlando points of interest
    // Taken from https://vacationidea.com/florida/best-things-to-do-in-orlando.html

    // Arrays are in [lon, lat].
    public static final Map<String, Double[]> locationsMap0 = new HashMap<>() {{
        put("Disney World", new Double[]{-81.579179,28.40572});
        put("MCO Airport", new Double[]{-81.306479,28.432306});
    }};
    public static final List<Map.Entry<String, Double[]>> locations = locationsMap0.entrySet().stream().collect(Collectors.toList());

        // Arrays are in [lon, lat].
    public static final Map<String, Double[]> locationsMap = new HashMap<>() {{
        // https://www.orlando.gov/Our-Government/History/Find-Historic-Landmarks
        put("AMTRAK-Orlando", new Double[]{-81.3816, 28.52504});
        put("The Beachham Theater", new Double[]{-81.379418, 28.543004});
        put("Bumby Hardware Building", new Double[]{-81.381086, 28.539738});
        put("Dickson-Azalea Park", new Double[]{-81.358345, 28.544297});
        put("Ebenezer United Methodist Church", new Double[]{ -81.396418, 28.515499});
        put("Baldwin Fairchild Funeral Home", new Double[]{-81.36496, 28.587161});
        put("Old Firestone Tire Factory", new Double[]{-81.3794, 28.5507});
        put("Dubsdread Golf Course", new Double[]{-81.38743, 28.58205});
        put("Kaley Elementary School", new Double[] { -81.358521, 28.520319});
        put("Marks Street Senior Rec Center", new Double[] {-81.3773731, 28.557355});
        put("Mount Zion Missionary Baptist Church", new Double[] {-81.386102, 28.54386});
        put("Church Street Station", new Double[] {-81.380556,28.54});
        put("Orwin Manor", new Double[]{-81.363782, 28.578752});

        // Other popular places?
        put("Disney World", new Double[]{-81.579179,28.40572});
        put("Universal's Islands of Adventure", new Double[]{-81.463638, 28.474903});
        put("Legoland Florida", new Double[]{-81.690511, 27.986779}); // Might not be able to plan.
        put("SeaWorld", new Double[]{-81.461563, 28.415277});
        put("MCO Airport", new Double[]{-81.306479,28.432306});
        put("University of Central Florida", new Double[] {-81.208169, 28.593648});
        put("Downtown Sanford", new Double[] {-81.2666431, 28.8109338});
        put("Mall at Millenia", new Double[] {-81.431498, 28.485635});
    }};
    public static final List<Map.Entry<String, Double[]>> locations11 = locationsMap.entrySet().stream().collect(Collectors.toList());

    public static final Map<String, String> modeParamsMap = new HashMap<>() {{
        put("Transit", "WALK%2CBUS%2CRAIL"); // Walk, Bus, Rail
        put("+Bike", "BICYCLE%2CBUS%2CRAIL"); // Own bicycle, Bus, Rail
        put("+BkSh", "BICYCLE_RENT%2CBUS%2CRAIL"); // Bikeshare, Bus, Rail
        put("+Hail", "CAR_HAIL%2CBUS%2CRAIL"); // Car hail, bus, rail
    }};
    public static final List<Map.Entry<String, String>> modeParams = modeParamsMap.entrySet().stream().collect(Collectors.toList());

}
