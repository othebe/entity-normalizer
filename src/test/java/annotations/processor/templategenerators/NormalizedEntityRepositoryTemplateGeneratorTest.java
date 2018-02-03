package annotations.processor.templategenerators;

import annotations.processor.testentities.devicetype.Device;
import annotations.processor.testentities.message.Message;
import annotations.processor.testentities.user.User;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import core.IEntity;
import entitynormalizer.store.NormalizedEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static annotations.processor.testentities.devicetype.DeviceList.DEVICE_ANDROID;
import static annotations.processor.testentities.devicetype.DeviceList.DEVICE_IOS;
import static annotations.processor.testentities.devicetype.DeviceList.DEVICE_WINDOWS;
import static annotations.processor.testentities.user.UserList.USER_FOZZY;
import static annotations.processor.testentities.user.UserList.USER_GOZZY;
import static annotations.processor.testentities.user.UserList.USER_OZZY;
import static org.junit.jupiter.api.Assertions.*;

public class NormalizedEntityRepositoryTemplateGeneratorTest {
    private static final long MESSAGE_ID = 1L;

    private Message message;

    private NormalizedEntityRepository repository;

    @BeforeEach
    public void init() {
        message = new Message(
            MESSAGE_ID,
            "Hello World",
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

        repository = new NormalizedEntityRepository();
    }

    @Test
    public void shouldStartEmpty() {
        assertNull(repository.getMessage(MESSAGE_ID));
    }

    @Test
    public void shouldExtractEntitiesWhenPut() {
        Set<IEntity> dirty = repository.put(message);

        assertEquals(dirty.size(), 7);
        assertTrue(dirty.contains(message));
        assertTrue(dirty.contains(USER_OZZY));
        assertTrue(dirty.contains(USER_FOZZY));
        assertTrue(dirty.contains(USER_GOZZY));
        assertTrue(dirty.contains(DEVICE_ANDROID));
        assertTrue(dirty.contains(DEVICE_IOS));
        assertTrue(dirty.contains(DEVICE_WINDOWS));
    }

    @Test
    public void shouldGetEntities() {
        repository.put(message);

        assertMessagesEquals(repository.getMessage(MESSAGE_ID), message);
        assertUsersEqual(repository.getUser(USER_OZZY.id()), USER_OZZY);
        assertUsersEqual(repository.getUser(USER_FOZZY.id()), USER_FOZZY);
        assertUsersEqual(repository.getUser(USER_GOZZY.id()), USER_GOZZY);
        assertDevicesEqual(repository.getDevice(DEVICE_ANDROID.id()), DEVICE_ANDROID);
        assertDevicesEqual(repository.getDevice(DEVICE_IOS.id()), DEVICE_IOS);
        assertDevicesEqual(repository.getDevice(DEVICE_WINDOWS.id()), DEVICE_WINDOWS);
    }

    @Test
    public void shouldUpdateSingleEntityFromSingleEntity() {
        User userOzzyCopy = new User(USER_OZZY.id(), "Ozzy-Copy");

        repository.put(USER_OZZY);
        repository.put(userOzzyCopy);

        assertUsersEqual(repository.getUser(USER_OZZY.id()), userOzzyCopy);
    }

    @Test
    public void shouldUpdateNestedEntityFromSingleEntity() {
        User userOzzyCopy = new User(USER_OZZY.id(), "Ozzy-Copy");

        repository.put(message);
        repository.put(userOzzyCopy);

        Message cachedMessage = repository.getMessage(message.id());

        assertUsersEqual(repository.getUser(USER_OZZY.id()), userOzzyCopy);
        assertUsersEqual(cachedMessage.getSender(), userOzzyCopy);
        List<User> deviceUsers = cachedMessage.getUsersByDevices().get(
                ImmutableList.of(DEVICE_ANDROID, DEVICE_IOS));
        assertEquals(deviceUsers, ImmutableList.of(userOzzyCopy, USER_FOZZY));
    }

    @Test
    public void shouldUpdateSingleEntityFromNestedEntity() {
        User userOzzyCopy = new User(USER_OZZY.id(), "Ozzy-Copy");

        repository.put(userOzzyCopy);
        repository.put(message);

        assertUsersEqual(repository.getUser(userOzzyCopy.id()), USER_OZZY);
    }

    @Test
    public void shouldUpdateNestedEntityFromNestedEntity() {
        User userFozzyCopy = new User(USER_FOZZY.id(), "Fozzy-Copy");
        User userGozzyCopy = new User(USER_GOZZY.id(), "Gozzy-Copy");

        Message messageCopy = new Message(
                MESSAGE_ID,
                "Hello World",
                USER_OZZY,
                ImmutableList.of(userFozzyCopy, userGozzyCopy),
                ImmutableList.<Map<User,Boolean>>of(
                        ImmutableMap.of(userFozzyCopy, true),
                        ImmutableMap.of(userGozzyCopy, false)),
                ImmutableMap.<List<Device>, List<User>>of(
                        ImmutableList.of(DEVICE_ANDROID, DEVICE_IOS),
                        ImmutableList.of(USER_OZZY, userFozzyCopy),

                        ImmutableList.of(DEVICE_WINDOWS),
                        ImmutableList.of(userGozzyCopy)));

        repository.put(message);
        repository.put(messageCopy);

        assertUsersEqual(repository.getUser(USER_FOZZY.id()), userFozzyCopy);
        assertUsersEqual(repository.getUser(USER_GOZZY.id()), userGozzyCopy);
    }

    private static void assertMessagesEquals(Message message1, Message message2) {
        assertEquals(message1.getMessageId(), message2.getMessageId());
        assertEquals(message1.getBody(), message2.getBody());
        assertEquals(message1.getSender(), message2.getSender());
        assertEquals(message1.getRecipients(), message2.getRecipients());
        assertEquals(message1.getRecipientsToReadList(), message2.getRecipientsToReadList());
        assertEquals(message1.getUsersByDevices(), message2.getUsersByDevices());
    }

    private static void assertUsersEqual(User user1, User user2) {
        assertEquals(user1.getUserId(), user2.getUserId());
        assertEquals(user1.getName(), user2.getName());
    }

    private static void assertDevicesEqual(Device device1, Device device2) {
        assertEquals(device1.getDeviceType(), device2.getDeviceType());
    }
}
