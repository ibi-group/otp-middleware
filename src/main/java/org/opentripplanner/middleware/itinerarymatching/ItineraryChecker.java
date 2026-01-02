package org.opentripplanner.middleware.itinerarymatching;

import org.opentripplanner.middleware.otp.LegFinder;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.utils.ItineraryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.opentripplanner.middleware.itinerarymatching.ItineraryFromLegMatcher.getTransitLegs;
import static org.opentripplanner.middleware.otp.OtpDispatcher.OTP_SERVER_REQUEST_TIMEOUT_IN_SECONDS;

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
        List<Leg> queriedLegs = new ArrayList<>();
        boolean useThreading = transitLegs.size() > 1 && ItineraryUtils.isOtpRequestThreadingEnabled();

        Map<String, Leg> legResponses = useThreading
            ? getThreadedLegResponses(transitLegs)
            : Collections.emptyMap();

        Map<String, String> legIdMap = new HashMap<>();
        for (Leg leg : transitLegs) {
            Leg returnedLeg = useThreading
                ? legResponses.get(leg.id)
                : legFinder.queryLeg(leg, targetDate);

            if (returnedLeg == null) {
                break;
            } else {
                queriedLegs.add(returnedLeg);
                legIdMap.put(leg.id, returnedLeg.id);
            }
        }

        return new ItineraryFromLegMatcher(itinerary, queriedLegs, legIdMap).process();
    }

    /**
     * Execute OTP requests and process the responses in a custom executor. Each response is assign to a day of the week.
     */
    private Map<String, Leg> getThreadedLegResponses(List<Leg> transitLegs) {
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        ConcurrentMap<String, CompletableFuture<Leg>> otpLegRequestTasks = assignOtpRequestByLeg(
            transitLegs,
            executor
        );

        Map<String, Leg> otpRequestResponses = new HashMap<>();

        otpLegRequestTasks.forEach((legId, future) -> {
            Leg legResponse = null;
            try {
                // Wait for completion and assign response.
                legResponse = future.join();
            } catch (CancellationException | CompletionException e) {
                LOG.error("Failed to get OTP leg response for {}.", legId, e);
            }
            LOG.debug("OTP leg response for {}: {}", legId, legResponse);
            otpRequestResponses.put(legId, legResponse);
        });

        executor.shutdown();
        try {
            if (!executor.awaitTermination(OTP_SERVER_REQUEST_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)) {
                LOG.warn(
                    "OTP requests terminated, time out reached ({} seconds). Shutting down executor.",
                    OTP_SERVER_REQUEST_TIMEOUT_IN_SECONDS
                );
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            LOG.warn("OTP requests were interrupted! Shutting down executor.", e);
            executor.shutdownNow();
        }
        return otpRequestResponses;
    }

    /**
     * Assign an OTP request to a leg and start each call async to the OTP server.
     */
    private ConcurrentMap<String, CompletableFuture<Leg>> assignOtpRequestByLeg(
        List<Leg> transitLegs,
        ExecutorService executor
    ) {
        return transitLegs
            .stream()
            .collect(Collectors.toConcurrentMap(
                leg -> leg.id,
                leg -> CompletableFuture.supplyAsync(
                    () -> legFinder.queryLeg(leg, targetDate),
                    executor)
                )
            );
    }
}
