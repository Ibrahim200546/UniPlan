package kz.uniplan.app.data.auth;

/** Allows deterministic auth tests without replacing the production Keystore implementation. */
public interface SessionStorage {
    void save(String value) throws Exception;
    String load() throws Exception;
    void clear() throws Exception;
}
