package org.opentripplanner.middleware.persistence;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.models.BugsnagEvent;
import org.opentripplanner.middleware.models.BugsnagEventRequest;
import org.opentripplanner.middleware.models.BugsnagProject;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static org.opentripplanner.middleware.OtpMiddlewareMain.getConfigPropertyAsText;

/**
 * Groups together a bunch of TypedPersistence abstractions around MongoDB Collections.
 */
public class Persistence {

    private static final Logger LOG = LoggerFactory.getLogger(Persistence.class);
    private static final String MONGO_PROTOCOL = getConfigPropertyAsText("MONGO_PROTOCOL", "mongodb");
    private static final String MONGO_HOST = getConfigPropertyAsText("MONGO_HOST", "localhost:27017");
    private static final String MONGO_USER = getConfigPropertyAsText("MONGO_USER");
    private static final String MONGO_PASSWORD = getConfigPropertyAsText("MONGO_PASSWORD");
    private static final String MONGO_DB_NAME = getConfigPropertyAsText("MONGO_DB_NAME");

    private static MongoClient mongoClient;
    private static MongoDatabase mongoDatabase;
    // One abstracted Mongo collection for each class of persisted objects
    public static TypedPersistence<OtpUser> otpUsers;
    public static TypedPersistence<AdminUser> adminUsers;
    public static TypedPersistence<ApiUser> apiUsers;
    public static TypedPersistence<TripRequest> tripRequests;
    public static TypedPersistence<TripSummary> tripSummaries;
    public static TypedPersistence<BugsnagEventRequest> bugsnagEventRequests;
    public static TypedPersistence<BugsnagEvent> bugsnagEvents;
    public static TypedPersistence<BugsnagProject> bugsnagProjects;
    public static TypedPersistence<MonitoredTrip> monitoredTrips;

    public static void initialize () {
        // TODO Add custom codec libraries
        CodecProvider pojoCodecProvider = PojoCodecProvider.builder()
            .register("org.opentripplanner.middleware.models")
            .automatic(true)
            .build();
        CodecRegistry pojoCodecRegistry = fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            fromProviders(pojoCodecProvider)
            // Additional custom codecs can be supplied here in a custom
            // registry using CodecRegistry#fromCodecs.
        );
        // Construct connection string from configuration values.
        String userAtPassword = MONGO_USER != null && MONGO_PASSWORD != null
            ? String.format("%s:%s@", MONGO_USER, MONGO_PASSWORD)
            : "";
        final String MONGO_URI = String.join("/", MONGO_HOST, MONGO_DB_NAME);
        ConnectionString connectionString = new ConnectionString(
            String.format(
                "%s://%s%s?retryWrites=true&w=majority",
                MONGO_PROTOCOL,
                userAtPassword,
                MONGO_URI
            )
        );
        MongoClientSettings settings = MongoClientSettings.builder()
            .codecRegistry(pojoCodecRegistry)
            .applyConnectionString(connectionString)
            .build();
        LOG.info("Connecting to MongoDB instance at {}://{}", MONGO_PROTOCOL, MONGO_URI);
        mongoClient = MongoClients.create(settings);
        mongoDatabase = mongoClient.getDatabase(MONGO_DB_NAME);
        otpUsers = new TypedPersistence(mongoDatabase, OtpUser.class);
        adminUsers = new TypedPersistence(mongoDatabase, AdminUser.class);
        apiUsers = new TypedPersistence(mongoDatabase, ApiUser.class);
        tripRequests = new TypedPersistence(mongoDatabase, TripRequest.class);
        tripSummaries = new TypedPersistence(mongoDatabase, TripSummary.class);
        bugsnagEventRequests = new TypedPersistence(mongoDatabase, BugsnagEventRequest.class);
        bugsnagEvents = new TypedPersistence(mongoDatabase, BugsnagEvent.class);
        bugsnagProjects = new TypedPersistence(mongoDatabase, BugsnagProject.class);
        monitoredTrips = new TypedPersistence(mongoDatabase, MonitoredTrip.class);
        // TODO Add other models...
    }

}
