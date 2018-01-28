package annotations.processor.testentities.devicetype;

import static annotations.processor.testentities.devicetype.DeviceType.*;

public class DeviceList {
    public static final Device DEVICE_ANDROID = new Device(ANDROID);
    public static final Device DEVICE_IOS = new Device(iOS);
    public static final Device DEVICE_WINDOWS = new Device(WINDOWS);
    public static final Device DEVICE_UNKNOWN = new Device(UNKNOWN);
}
