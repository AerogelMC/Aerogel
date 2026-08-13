package dev.aerogel.api.dialog;

@FunctionalInterface
public interface DialogCallback {
    void accept(DialogResult result) throws Exception;
}
