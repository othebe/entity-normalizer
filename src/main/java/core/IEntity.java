package core;

public interface IEntity<ID> {
    // ID for this entity.
    ID id();

    // Type of this entity.
    String entityType();
}
