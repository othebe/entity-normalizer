package annotations.processor.testentities.user;

import annotations.EntityId;
import annotations.EntitySpec;

@EntitySpec(name = "User")
public class UserSpec {
    @EntityId
    private long userId;

    private String name;
}
