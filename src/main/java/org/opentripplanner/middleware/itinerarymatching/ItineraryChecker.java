package org.opentripplanner.middleware.itinerarymatching;

import org.opentripplanner.middleware.otp.LegFinder;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.utils.ItineraryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.opentripplanner.middleware.itinerarymatching.ItineraryFromLegMatcher.getTransitLegs;

/**
 * Helper class for performing an itinerary existence check.
 */
public class ItineraryChecker {
    private static final Logger LOG = LoggerFactory.getLogger(ItineraryChecker.class);

    private final Itinerary itinerary;

    private final LegFinder legFinder;

    private final LocalDate targetDate;

    public ItineraryChecker(Itinerary itinerary, LegFinder legFinder, LocalDate targetDate) {
        this.itinerary = itinerary;
        this.legFinder = legFinder;
        this.targetDate = targetDate;
    }
    /**
     * Check leg existence and returns status and delay information
     */
    public ItineraryCheckStatus checkLegs() {
        List<Leg> transitLegs = getTransitLegs(itinerary.legs);
        boolean useThreading = transitLegs.size() > 1 && ItineraryUtils.isOtpRequestThreadingEnabled();

        Map<String, Leg> legResponses = useThreading
            ? getLegResponsesThreaded(transitLegs)
            : getLegResponsesNonThreaded(transitLegs);

        return new ItineraryFromLegMatcher(itinerary, legResponses).process();
    }

    private Map<String, Leg> getLegResponsesNonThreaded(List<Leg> transitLegs) {
        Map<String, Leg> legMap = new HashMap<>();
        for (Leg leg : transitLegs) {
            Leg returnedLeg = legFinder.queryLeg(leg, targetDate);
            // Skip subsequent calls if one leg is not found (the itinerary cannot be reconstructed).
            if (returnedLeg == null) {
                break;
            } else {
                legMap.put(leg.id, returnedLeg);
            }
        }
        return legMap;
    }

    /**
     * Execute OTP requests and process the responses in a custom executor. Each response is assigned to a leg.
     */
    private Map<String, Leg> getLegResponsesThreaded(List<Leg> transitLegs) {
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        Map<String, Leg> otpLegResponses = ItineraryUtils.collectResponses(
            transitLegs
                .stream()
                .collect(Collectors.toConcurrentMap(
                    leg -> leg.id,
                    leg -> CompletableFuture.supplyAsync(() -> legFinder.queryLeg(leg, targetDate), executor)
                )),
            new HashMap<>(),
            LOG,
            "OTP leg response"
        );
        OtpDispatcher.waitForTimeoutThenCancelPendingRequests(executor);
        return otpLegResponses;
    }
}
