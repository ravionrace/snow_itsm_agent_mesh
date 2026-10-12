package com.acme.itsm.orchestrator.session;

import com.acme.itsm.orchestrator.OrchestratorProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Configuration
public class SessionStores {

    @Bean
    public SessionStore sessionStore(OrchestratorProperties props, ObjectMapper mapper,
                                     @Value("${spring.data.mongodb.uri:mongodb://localhost:27017/itsm_mesh}") String mongoUri) {
        if ("memory".equalsIgnoreCase(props.getSessions().getStore())) {
            return new InMemory();
        }
        return new Mongo(mongoUri, new VaultCipher(props.getSessions().getVaultKey()), mapper, props.getSessions());
    }

    static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** Local development and tests. */
    public static final class InMemory implements SessionStore {
        private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();

        @Override
        public ChatSession create(String userId) {
            ChatSession s = new ChatSession(newId(), userId, Instant.now(), Map.of());
            sessions.put(s.id(), s);
            return s;
        }

        @Override
        public Optional<ChatSession> find(String id, String userId) {
            return Optional.ofNullable(sessions.get(id)).filter(s -> s.userId().equals(userId));
        }

        @Override
        public void saveVault(String id, Map<String, String> vault) {
            sessions.computeIfPresent(id, (k, s) -> new ChatSession(s.id(), s.userId(), s.createdAt(), Map.copyOf(vault)));
        }
    }

    /** Sessions in MongoDB with a TTL index; the vault is encrypted with AES-GCM. */
    static final class Mongo implements SessionStore {
        private final com.mongodb.client.MongoCollection<Document> col;
        private final VaultCipher cipher;
        private final ObjectMapper mapper;

        Mongo(String uri, VaultCipher cipher, ObjectMapper mapper, OrchestratorProperties.Sessions cfg) {
            com.mongodb.ConnectionString cs = new com.mongodb.ConnectionString(uri);
            this.col = MongoClients.create(cs).getDatabase(cs.getDatabase() == null ? "itsm_mesh" : cs.getDatabase())
                    .getCollection("chat_sessions");
            this.col.createIndex(Indexes.ascending("lastActiveAt"),
                    new IndexOptions().expireAfter(cfg.getTtl().toSeconds(), TimeUnit.SECONDS));
            this.cipher = cipher;
            this.mapper = mapper;
        }

        @Override
        public ChatSession create(String userId) {
            String id = newId();
            Date now = new Date();
            col.insertOne(new Document("_id", id).append("userId", userId).append("createdAt", now)
                    .append("lastActiveAt", now).append("vault", cipher.encrypt("{}")));
            return new ChatSession(id, userId, now.toInstant(), Map.of());
        }

        @Override
        public Optional<ChatSession> find(String id, String userId) {
            Document d = col.find(new Document("_id", id).append("userId", userId)).first();
            if (d == null) {
                return Optional.empty();
            }
            try {
                Map<String, String> vault = mapper.readValue(cipher.decrypt(d.getString("vault")),
                        new TypeReference<HashMap<String, String>>() { });
                return Optional.of(new ChatSession(id, userId, d.getDate("createdAt").toInstant(), vault));
            } catch (Exception e) {
                throw new IllegalStateException("cannot read session vault", e);
            }
        }

        @Override
        public void saveVault(String id, Map<String, String> vault) {
            try {
                col.updateOne(new Document("_id", id), new Document("$set", new Document("vault",
                        cipher.encrypt(mapper.writeValueAsString(vault))).append("lastActiveAt", new Date())));
            } catch (Exception e) {
                throw new IllegalStateException("cannot save session vault", e);
            }
        }
    }

    /** AES-256-GCM with a random 12-byte nonce per value. */
    static final class VaultCipher {
        private final SecretKeySpec key;
        private final SecureRandom random = new SecureRandom();

        VaultCipher(String base64Key) {
            if (base64Key == null || base64Key.isBlank()) {
                throw new IllegalStateException("itsm.sessions.vault-key (base64, 32 bytes) is required for the mongo session store");
            }
            byte[] raw = Base64.getDecoder().decode(base64Key);
            if (raw.length != 32) {
                throw new IllegalStateException("itsm.sessions.vault-key must decode to 32 bytes");
            }
            this.key = new SecretKeySpec(raw, "AES");
        }

        String encrypt(String plain) {
            try {
                byte[] nonce = new byte[12];
                random.nextBytes(nonce);
                Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
                byte[] ct = c.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + ct.length).put(nonce).put(ct).array());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        String decrypt(String encoded) {
            try {
                byte[] all = Base64.getDecoder().decode(encoded);
                Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
                return new String(c.doFinal(all, 12, all.length - 12), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
