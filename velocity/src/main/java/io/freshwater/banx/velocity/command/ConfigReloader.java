package io.freshwater.banx.velocity.command;

/** Callback used by the reload command to ask the plugin to re-read its configuration. */
@FunctionalInterface
public interface ConfigReloader {
    void reload() throws Exception;
}
