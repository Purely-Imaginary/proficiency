package dev.amman.proficiency.platform.bus;

/** Base of every event on the bus. Cancellation only means something on {@link ICancellableEvent}s. */
public abstract class Event {

    private boolean canceled;

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        if (!(this instanceof ICancellableEvent)) {
            throw new UnsupportedOperationException(getClass().getSimpleName() + " cannot be canceled");
        }
        this.canceled = canceled;
    }
}
