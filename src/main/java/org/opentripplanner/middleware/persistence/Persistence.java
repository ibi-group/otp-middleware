package org.opentripplanner.middleware.persistence;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.opentripplanner.middleware.models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * Groups together a bunch of TypedPersistence abstractions around MongoDB Collections.
 */
public class Persistence {

    private static final Logger LOG = LoggerFactory.getLogger(Persistence.class);
    private static final String MONGO_URI = "MONGO_URI";
    private static final String MONGO_DB_NAME = "MONGO_DB_NAME";

    private static MongoClient mongo;
    private static MongoDatabase mongoDatabase;
    private static CodecRegistry pojoCodecRegistry;

    // One abstracted Mongo collection for each class of persisted objects
    public static TypedPersistence<User> users;

    public static void initialize () {

//        PojoCodecProvider pojoCodecProvider = PojoCodecProvider.builder()
//            .register("com.conveyal.datatools.manager.jobs")
//            .register("com.conveyal.datatools.manager.models")
//            .register("com.conveyal.gtfs.loader")
//            .register("com.conveyal.gtfs.validator")
//            .automatic(true)
//            .build();

        // Register our custom codecs which cannot be properly auto-built by reflection
//        CodecRegistry customRegistry = CodecRegistries.fromCodecs(
//            new IntArrayCodec(),
//            new URLCodec(),
//            new LocalDateCodec()
//        );

        // TODO Add custom codec libraries
//        pojoCodecRegistry = fromRegistries(MongoClient.getDefaultCodecRegistry(),
////            customRegistry,
//            fromProviders(pojoCodecProvider)
//        );

        CodecProvider pojoCodecProvider = PojoCodecProvider.builder()
            .register("org.opentripplanner.middleware.models")
            .automatic(true)
            .build();
        CodecRegistry pojoCodecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), fromProviders(pojoCodecProvider));
//        MongoClientOptions.Builder builder = MongoClientOptions.builder()
////                .sslEnabled(true)
//            .codecRegistry(pojoCodecRegistry);
        MongoClientSettings settings = MongoClientSettings.builder()
            .codecRegistry(pojoCodecRegistry)
            .build();
        // TODO Add way to use external DB
        LOG.info("Connecting to local MongoDB instance");
        mongo = MongoClients.create(settings);

        // TODO Add configurable db name
        mongoDatabase = mongo.getDatabase("otp_middleware");
        users = new TypedPersistence(mongoDatabase, User.class);

        // TODO: Set up indexes on feed versions by feedSourceId, version #? deployments, feedSources by projectId.
//        deployments.getMongoCollection().createIndex(Indexes.descending("projectId"));
//        feedSources.getMongoCollection().createIndex(Indexes.descending("projectId"));
//        feedVersions.getMongoCollection().createIndex(Indexes.descending("feedSourceId", "version"));
//        snapshots.getMongoCollection().createIndex(Indexes.descending("feedSourceId", "version"));
    }

}
