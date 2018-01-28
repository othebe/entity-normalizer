package annotations.processor.testentities.devicetype;

import annotations.EntityId;
import annotations.EntitySpec;

@EntitySpec(name = "Device")
public class DeviceSpec {
    @EntityId
    private DeviceType deviceType;
}
