package io.freshwater.banx.api;

/**
 * Static access point for the {@link FreshwaterBanXAPI}.
 *
 * <p>The plugin registers the implementation during startup. Third-party plugins should call
 * {@link #get()} lazily (e.g. inside event handlers), not in static initializers.</p>
 */
public final class FreshwaterBanXProvider {

    private static volatile FreshwaterBanXAPI instance;

    private FreshwaterBanXProvider() {
    }

    /**
     * @throws IllegalStateException if FreshwaterBanX has not finished initializing
     */
    public static FreshwaterBanXAPI get() {
        FreshwaterBanXAPI api = instance;
        if (api == null) {
            throw new IllegalStateException("FreshwaterBanX API is not initialized yet");
        }
        return api;
    }

    public static boolean isAvailable() {
        return instance != null;
    }

    public static void register(FreshwaterBanXAPI api) {
        instance = api;
    }

    public static void unregister() {
        instance = null;
    }
}
