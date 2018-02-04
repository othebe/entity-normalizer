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

        repository = NormalizedEntityRepository.builder().build();
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

        assertEquals(repository.getMessage(MESSAGE_ID), message);
        assertEquals(repository.getUser(USER_OZZY.id()), USER_OZZY);
        assertEquals(repository.getUser(USER_FOZZY.id()), USER_FOZZY);
        assertEquals(repository.getUser(USER_GOZZY.id()), USER_GOZZY);
        assertEquals(repository.getDevice(DEVICE_ANDROID.id()), DEVICE_ANDROID);
        assertEquals(repository.getDevice(DEVICE_IOS.id()), DEVICE_IOS);
        assertEquals(repository.getDevice(DEVICE_WINDOWS.id()), DEVICE_WINDOWS);
    }

    @Test
    public void shouldUpdateSingleEntityFromSingleEntity() {
        User userOzzyCopy = new User(USER_OZZY.id(), "Ozzy-Copy");

        repository.put(USER_OZZY);
        repository.put(userOzzyCopy);

        assertEquals(repository.getUser(USER_OZZY.id()), userOzzyCopy);
    }

    @Test
    public void shouldUpdateNestedEntityFromSingleEntity() {
        User userOzzyCopy = new User(USER_OZZY.id(), "Ozzy-Copy");

        repository.put(message);
        repository.put(userOzzyCopy);

        Message cachedMessage = repository.getMessage(message.id());

        assertEquals(repository.getUser(USER_OZZY.id()), userOzzyCopy);
        assertEquals(cachedMessage.getSender(), userOzzyCopy);
        List<User> deviceUsers = cachedMessage.getUsersByDevices().get(
                ImmutableList.of(DEVICE_ANDROID, DEVICE_IOS));
        assertEquals(deviceUsers, ImmutableList.of(userOzzyCopy, USER_FOZZY));
    }

    @Test
    public void shouldUpdateSingleEntityFromNestedEntity() {
        User userOzzyCopy = new User(USER_OZZY.id(), "Ozzy-Copy");

        repository.put(userOzzyCopy);
        repository.put(message);

        assertEquals(repository.getUser(userOzzyCopy.id()), USER_OZZY);
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

        assertEquals(repository.getUser(USER_FOZZY.id()), userFozzyCopy);
        assertEquals(repository.getUser(USER_GOZZY.id()), userGozzyCopy);
    }
}
