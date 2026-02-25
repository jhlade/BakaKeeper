package cz.zsstudanka.skola.bakakeeper.service;

import lombok.Getter;

/**
 * Výsledek jedné synchronizační operace.
 *
 * @author Jan Hladěna
 */
@Getter
public class SyncResult {

    /** Typ výsledku. */
    public enum Type {
        CREATED, UPDATED, DELETED, RETIRED, PAIRED,
        ERROR, SKIPPED, NO_CHANGE
    }

    private final Type type;
    private final String entityId;
    private final String description;

    public SyncResult(Type type, String entityId, String description) {
        this.type = type;
        this.entityId = entityId;
        this.description = description;
    }

    public boolean isSuccess() {
        return type != Type.ERROR;
    }

    public static SyncResult created(String id, String desc) {
        return new SyncResult(Type.CREATED, id, desc);
    }

    public static SyncResult updated(String id, String desc) {
        return new SyncResult(Type.UPDATED, id, desc);
    }

    public static SyncResult retired(String id, String desc) {
        return new SyncResult(Type.RETIRED, id, desc);
    }

    public static SyncResult paired(String id, String desc) {
        return new SyncResult(Type.PAIRED, id, desc);
    }

    public static SyncResult noChange(String id) {
        return new SyncResult(Type.NO_CHANGE, id, null);
    }

    public static SyncResult noChange(String id, String desc) {
        return new SyncResult(Type.NO_CHANGE, id, desc);
    }

    public static SyncResult error(String id, String desc) {
        return new SyncResult(Type.ERROR, id, desc);
    }

    public static SyncResult skipped(String id, String desc) {
        return new SyncResult(Type.SKIPPED, id, desc);
    }

    @Override
    public String toString() {
        return type + " [" + entityId + "]" + (description != null ? " " + description : "");
    }
}
