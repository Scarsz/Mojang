package github.scarsz.mojang;

import com.github.kevinsawicki.http.HttpRequest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import github.scarsz.mojang.exception.ProfileFetchException;
import net.jodah.expiringmap.ExpiringMap;

import java.io.File;
import java.io.Serializable;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class Mojang {

    private static final ExecutorService executor = Executors.newFixedThreadPool(5);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final ExpiringMap<UUID, GameProfile> profileCache = ExpiringMap.builder().variableExpiration().expiration(1, TimeUnit.DAYS).build();

    private static Connection connection = null;
    private static String userAgent = "Mojang";

    public static void setDatabaseFile(File file) {
        try {
            Class.forName("org.h2.Driver");
            if (connection != null) closeDatabase();
            connection = DriverManager.getConnection("jdbc:h2:" + file.getAbsolutePath());
            connection.prepareStatement("CREATE TABLE IF NOT EXISTS `profiles` ( `uuid` UUID NOT NULL, `username` VARCHAR NOT NULL, `previousNames` VARCHAR NOT NULL, `skin` VARCHAR NOT NULL, `retrieved` BIGINT NOT NULL)").executeUpdate();
            try { connection.prepareStatement("CREATE UNIQUE INDEX `idx_uuid` ON `profiles` (`uuid`)").executeUpdate(); } catch (Exception ignored) {}
            ResultSet result = connection.prepareStatement("SELECT * FROM `profiles`").executeQuery();
            while (result.next()) {
                long timeSinceRetrieved = System.currentTimeMillis() - result.getLong("retrieved");
                if (timeSinceRetrieved < TimeUnit.DAYS.toMillis(1)) {
                    GameProfile profile = new GameProfile(
                            UUID.fromString(result.getString("uuid")),
                            result.getString("username"),
                            Arrays.asList(result.getString("previousNames").split(" ")),
                            result.getString("skin")
                    );
                    profileCache.put(profile.uuid, profile, TimeUnit.DAYS.toMillis(1) - timeSinceRetrieved, TimeUnit.MILLISECONDS);
                }
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
    public static void setUserAgent(String userAgent) {
        Mojang.userAgent = userAgent;
    }
    public static void setExpiration(long duration, TimeUnit unit) {
        profileCache.setExpiration(duration, unit);
    }

    public static GameProfile fetch(UUID uuid) throws ProfileFetchException {
        return fetch(uuid.toString());
    }
    public static GameProfile fetch(String target) throws ProfileFetchException {
        if (target == null) return null;

        UUID targetUuid = null;
        try {
            targetUuid = UUID.fromString(target);
        } catch (IllegalArgumentException e) {
            for (Map.Entry<UUID, GameProfile> entry : profileCache.entrySet()) {
                GameProfile profile = entry.getValue();
                if (profile.name.equalsIgnoreCase(target)) {
                    targetUuid = profile.uuid;
                }
            }
        }

        if (targetUuid != null && profileCache.containsKey(targetUuid)) {
            return profileCache.get(targetUuid);
        } else {
            HttpRequest request = HttpRequest.get("https://api.ashcon.app/mojang/v1/user/" + target);
            request.userAgent(userAgent);
            int status = request.code();
            String body = request.body();
            switch (status) {
                case 200: {
                    try {
                        Map response = gson.fromJson(body, Map.class);
                        UUID uuid = UUID.fromString((String) response.get("uuid"));
                        String username = (String) response.get("username");
                        List<String> usernames = new LinkedList<>();
                        for (Object oHistory : ((ArrayList) response.get("username_history"))) {
                            Map history = (Map) oHistory;
                            usernames.add((String) history.get("username"));
                        }
                        String skin = ((String) ((Map) ((Map) response.get("textures")).get("skin")).get("url")).replaceFirst("https?://textures\\.minecraft\\.net/texture/", "");
                        GameProfile profile = new GameProfile(uuid, username, usernames, skin);
                        cache(profile);
                        return profile;
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.println(body);
                        throw new ProfileFetchException(target, e);
                    }
                }
                case 404: {
                    return null;
                }
                default: {
                    System.err.println("Bad response from Mojang API for " + target + " -> " + status + " " + body);
                    return null;
                }
            }
        }
    }

    public static Future<GameProfile> fetchLater(UUID uuid) {
        return fetchLater(uuid.toString());
    }

    public static Future<GameProfile> fetchLater(String target) {
        return executor.submit(() -> fetch(target));
    }

    public static void cache(GameProfile profile) {
        profileCache.put(profile.uuid, profile);

        if (connection != null) {
            try {
                try (PreparedStatement existsStatement = connection.prepareStatement("SELECT 1 FROM `profiles` WHERE `uuid` = ? LIMIT 1")) {
                    existsStatement.setObject(1, profile.uuid);
                    boolean exists = existsStatement.executeQuery().isBeforeFirst();

                    if (exists) {
                        try (PreparedStatement statement = connection.prepareStatement("UPDATE `profiles` SET (username, previousNames, skin, retrieved) = (?, ?, ?, ?) WHERE `uuid` = ?")) {
                            statement.setString(1, profile.name);
                            statement.setString(2, String.join(" ", profile.previousUsernames));
                            statement.setString(3, profile.skin);
                            statement.setLong(4, System.currentTimeMillis());
                            statement.setObject(5, profile.uuid);
                            statement.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO `profiles` VALUES (?, ?, ?, ?, ?)")) {
                            statement.setObject(1, profile.uuid);
                            statement.setString(2, profile.name);
                            statement.setString(3, String.join(" ", profile.previousUsernames));
                            statement.setString(4, profile.skin);
                            statement.setLong(5, System.currentTimeMillis());
                            statement.executeUpdate();
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static void cache(List<GameProfile> profiles) {
        if (connection != null) {
            try {
                connection.setAutoCommit(false);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        profiles.forEach(Mojang::cache);

        if (connection != null) {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static void closeDatabase() throws SQLException {
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    public static class GameProfile implements Serializable {

        private static final long serialVersionUID = 7472397552667171485L;
        private final UUID uuid;
        private final String name;
        private final List<String> previousUsernames;
        private final String skin;

        public GameProfile(UUID uuid, String name, List<String> previousUsernames, String skin) {
            this.uuid = uuid;
            this.name = name;
            this.previousUsernames = previousUsernames;
            this.skin = skin;
        }

        public UUID getUuid() {
            return uuid;
        }

        public String getName() {
            return name;
        }

        public List<String> getPreviousUsernames() {
            return previousUsernames;
        }

        public String getSkin() {
            return skin;
        }

        @Override
        public String toString() {
            return "GameProfile{" +
                    "uuid=" + uuid +
                    "name='" + name + '\'' +
                    '}';
        }

    }

}
