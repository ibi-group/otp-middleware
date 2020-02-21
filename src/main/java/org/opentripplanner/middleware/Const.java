package org.opentripplanner.middleware;

public class Const {
    // Play with some HTTP requests
    public static final int numItineraries = 3;
    public static final String[] urls = new String[] {
            // Each request made individually below from OTP MOD UI
            // is in reality two requests, one with and one without realtime updates.

            "https://fdot-otp-server.ibi-transit.com/otp/routers/default/plan?fromPlace=Ebenezer+United+Methodist+Church%3A%3A28.515499%2C-81.396418&toPlace=AMTRAK-Orlando%3A%3A28.52504%2C-81.3816&mode=BICYCLE%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1609&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&numItineraries=3",
            "https://fdot-otp-server.ibi-transit.com/otp/routers/default/plan?fromPlace=Ebenezer+United+Methodist+Church%3A%3A28.515499%2C-81.396418&toPlace=AMTRAK-Orlando%3A%3A28.52504%2C-81.3816&mode=BICYCLE_RENT%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1609&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&numItineraries=3",
            "https://fdot-otp-server.ibi-transit.com/otp/routers/default/plan?fromPlace=Ebenezer+United+Methodist+Church%3A%3A28.515499%2C-81.396418&toPlace=AMTRAK-Orlando%3A%3A28.52504%2C-81.3816&mode=CAR_HAIL%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1609&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&numItineraries=3",
            "https://fdot-otp-server.ibi-transit.com/otp/routers/default/plan?fromPlace=Ebenezer+United+Methodist+Church%3A%3A28.515499%2C-81.396418&toPlace=AMTRAK-Orlando%3A%3A28.52504%2C-81.3816&mode=WALK%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1609&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&numItineraries=3",

            "https://fdot-otp-server.ibi-transit.com/otp/routers/default/plan?fromPlace=Baldwin+Fairchild+Funeral+Home%3A%3A28.587161%2C-81.36496&toPlace=Kaley+Elementary+School%3A%3A28.520319%2C-81.358521&mode=BICYCLE%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1609&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&numItineraries=3",
            "https://fdot-otp-server.ibi-transit.com/otp/routers/default/plan?fromPlace=Baldwin+Fairchild+Funeral+Home%3A%3A28.587161%2C-81.36496&toPlace=Kaley+Elementary+School%3A%3A28.520319%2C-81.358521&mode=BICYCLE_RENT%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1609&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&numItineraries=3",
            "https://fdot-otp-server.ibi-transit.com/otp/routers/default/plan?fromPlace=Baldwin+Fairchild+Funeral+Home%3A%3A28.587161%2C-81.36496&toPlace=Kaley+Elementary+School%3A%3A28.520319%2C-81.358521&mode=CAR_HAIL%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1609&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&numItineraries=3",
            "https://fdot-otp-server.ibi-transit.com/otp/routers/default/plan?fromPlace=Baldwin+Fairchild+Funeral+Home%3A%3A28.587161%2C-81.36496&toPlace=Kaley+Elementary+School%3A%3A28.520319%2C-81.358521&mode=WALK%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1609&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&numItineraries=3",

            "https://fdot-otp-server.ibi-transit.com/otp/routers/default/plan?fromPlace=Dubsdread+Golf+Course%3A%3A28.58205%2C-81.38743&toPlace=Orwin+Manor%3A%3A28.578752%2C-81.363782&mode=BICYCLE%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1609&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&numItineraries=3",
            "https://fdot-otp-server.ibi-transit.com/otp/routers/default/plan?fromPlace=Dubsdread+Golf+Course%3A%3A28.58205%2C-81.38743&toPlace=Orwin+Manor%3A%3A28.578752%2C-81.363782&mode=BICYCLE_RENT%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1609&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&numItineraries=3",
            "https://fdot-otp-server.ibi-transit.com/otp/routers/default/plan?fromPlace=Dubsdread+Golf+Course%3A%3A28.58205%2C-81.38743&toPlace=Orwin+Manor%3A%3A28.578752%2C-81.363782&mode=CAR_HAIL%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1609&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&numItineraries=3",
            "https://fdot-otp-server.ibi-transit.com/otp/routers/default/plan?fromPlace=Dubsdread+Golf+Course%3A%3A28.58205%2C-81.38743&toPlace=Orwin+Manor%3A%3A28.578752%2C-81.363782&mode=WALK%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1609&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&numItineraries=3"
    };
}
