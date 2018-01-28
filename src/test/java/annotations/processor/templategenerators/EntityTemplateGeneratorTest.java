package annotations.processor.templategenerators;

import annotations.processor.testentities.devicetype.Device;
import annotations.processor.testentities.message.Message;
import annotations.processor.testentities.user.User;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static annotations.processor.testentities.devicetype.DeviceList.DEVICE_ANDROID;
import static annotations.processor.testentities.devicetype.DeviceList.DEVICE_IOS;
import static annotations.processor.testentities.devicetype.DeviceList.DEVICE_WINDOWS;
import static annotations.processor.testentities.user.UserList.USER_FOZZY;
import static annotations.processor.testentities.user.UserList.USER_GOZZY;
import static annotations.processor.testentities.user.UserList.USER_OZZY;
import static org.junit.jupiter.api.Assertions.*;

public class EntityTemplateGeneratorTest {
    private static final long MESSAGE_ID = 1L;
    private static final String MESSAGE_BODY = "Hello World";

    private Message message;

    @BeforeEach
    void init() {
        message = new Message(
                MESSAGE_ID,
                MESSAGE_BODY,
                USER_OZZY,
                ImmutableList.of(USER_FOZZY, USER_GOZZY),
                ImmutableList.<Map<User,Boolean>>of(
                        ImmutableMap.of(USER_FOZZY, true),
                        ImmutableMap.of(USER_GOZZY, false)),
                ImmutableMap.<List<Device>, List<User>>of(
                        ImmutableList.of(DEVICE_ANDROID, DEVICE_IOS),
                        ImmutableList.of(USER_OZZY, USER_FOZZY),

                        ImmutableList.of(DEVICE_WINDOWS),
                        ImmutableList.of(USER_GOZZY)));
    }

    @Test
    public void shouldCreateGetters() {
        assertEquals(message.getMessageId(), MESSAGE_ID);
        assertEquals(message.getBody(), MESSAGE_BODY);
        assertEquals(message.getSender(), USER_OZZY);
        assertSame(message.id(), message.getMessageId());

        List<User> recipients = message.getRecipients();
        assertEquals(recipients.size(), 2);
        assertEquals(recipients.get(0), USER_FOZZY);
        assertEquals(recipients.get(1), USER_GOZZY);

        List<Map<User, Boolean>> recipientsToReadList = message.getRecipientsToReadList();
        assertEquals(recipientsToReadList.size(), 2);
        assertTrue(recipientsToReadList.get(0).get(USER_FOZZY));
        assertFalse(recipientsToReadList.get(1).get(USER_GOZZY));

        Map<List<Device>, List<User>> usersByDevices = message.getUsersByDevices();
        assertEquals(usersByDevices.get(ImmutableList.of(DEVICE_ANDROID, DEVICE_IOS)), ImmutableList.of(USER_OZZY, USER_FOZZY));
        assertEquals(usersByDevices.get(ImmutableList.of(DEVICE_WINDOWS)), ImmutableList.of(USER_GOZZY));
    }
}
