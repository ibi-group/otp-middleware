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
import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;

/**
 * Groups together a bunch of TypedPersistence abstractions around MongoDB Collections.
 */
public class Persistence {

    private static final Logger LOG = LoggerFactory.getLogger(Persistence.class);
//    private static final String MONGO_URI = "MONGO_URI";
    private static final String MONGO_DB_NAME = getConfigPropertyAsText("MONGO_DB_NAME");

    private static MongoClient mongo;
    private static MongoDatabase mongoDatabase;
    // One abstracted Mongo collection for each class of persisted objects
    public static TypedPersistence<User> users;

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

        MongoClientSettings settings = MongoClientSettings.builder()
            .codecRegistry(pojoCodecRegistry)
            .build();
        // TODO Add way to use external DB
        LOG.info("Connecting to local MongoDB instance db={}", MONGO_DB_NAME);
        mongo = MongoClients.create(settings);
        mongoDatabase = mongo.getDatabase(MONGO_DB_NAME);
        users = new TypedPersistence(mongoDatabase, User.class);
        // TODO Add other models...
    }

}
