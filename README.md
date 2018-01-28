# entity-normalizer
Annotation based tool to provide automatic normalization of entities.

## Overview
Create entity specifications within your application to generate entity classes and a normalized store for said entities.

## Examples
Create entity specifications using the [EntitySpec](https://github.com/othebe/entity-normalizer/blob/master/src/main/java/annotations/EntitySpec.java) annotation. Every entity also requires an ID to be specified using the [EntityId](https://github.com/othebe/entity-normalizer/blob/master/src/main/java/annotations/EntityId.java) annotation.

    @EntitySpec(name = "User")
    public class UserSpec {
      @EntityId
      private long userId;

      private String name;
    }

This generates the following ``User`` entity:

    User user = new User(10L, "Foo");
    long userId = user.getUserId();   // 10L
    String name = user.getName();     // "Foo"
  
This also generates a ``NormalizedEntityStore``:

    long userId = user.getUserId();   // 10L

    NormalizedEntityStore store = new NormalizedEntityStore();
    store.put(user);
    String name = store.getUser(userId).getName();    // "Foo"
    
    User userUpdated = new User(userId, "Bar");
    store.put(userUpdated);
    String nameUpdated = store.getUser(userId).getName();   // "Bar"
    
Entities can be nested within other entities:

    @EntitySpec(name = "Message")
    public class MessageSpec {
      @EntityId
      private long messageId;
      
      private User sender;
    }
    
Nested entities are normalized through the store:

    User existing = store.getUser(userId);      // null

    Message message = new Message(messageId, user);
    store.put(message);
    
    User cached = store.getUser(userId);
    String name = cached.getName();   // "Foo"
    
    Message messageUpdated - new Message(messageId, userUpdated);
    store.put(messageUpdated);
    
    User cachedUpdated = store.getUser(userId);
    String name = cachedUpdated.getName();    // "Bar"
    
or conversely,

    Message message = new Message(messageId, user);
    store.put(message);
    
    Message cached = store.getMessage(messageId);
    String senderName = cached.getSender().getName();   // Foo
    
    store.put(userUpdated);
    cached = store.getMessage(messageId);
    senderName = cached.getSender().getName();    // "Bar"
    
Entities can be nested within parameters within a ``List`` or ``Map``, or a combination of both. The ``NormalizedEntityStore`` manages the normalization of all entities. In the example below, adding a ``Message`` instance to the store should normalize any instances of ``User`` or ``Message`` within the nested parameter field. Similarly, updating a single instance of a ``Message`` or ``User`` guarantees that ``getMessage(...)`` returns a ``Message`` with the latest updated version of the entity.
    
    @EntitySpec(name = "Message")
    public class MessageSpec {
      @EntityId
      private long messageId;
      
      private User sender;
      private List<User> recipients;
      private Map<User, Boolean> userReadMap;
      private Map<Map<User, Message>, List<Message>> someMegaMap;
      private List<Map<User, List<Map<Message, User>>>> someMegaList;
    }
    
The ``NormalizedEntityStore`` returns a list of possibly dirty entities which includes the Entity being added, as well as any others that may appear as properties or parameterized properties.

    List<IEntity> dirty = store.put(message);   // Contains all User and Message entities.

## Notes
- Rebuild project to generate classes.
- Ensure that the generated-sources directory is marked correctly in your IDE or pom.xml.
- Ensure that the generated-sources directory points to the correct location. For Maven users:

    ``<generatedSourcesDirectory>${project.build.directory}/generated-sources</generatedSourcesDirectory>``
