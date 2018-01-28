package annotations.processor.testentities.message;

import annotations.EntityId;
import annotations.EntitySpec;
import annotations.processor.testentities.devicetype.Device;
import annotations.processor.testentities.user.User;

import java.util.List;
import java.util.Map;

@EntitySpec(name = "Message")
public class MessageSpec {
    @EntityId
    private long messageId;

    private String body;
    private User sender;
    private List<User> recipients;
    private List<Map<User, Boolean>> recipientsToReadList;
    private Map<List<Device>, List<User>> usersByDevices;
}
