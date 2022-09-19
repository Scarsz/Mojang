# Mojang
Library for easy usage of Mojang heads &amp; profiles

# Usage
```java
Head.createFromTexture("26e27da12819a8b053da0cc2b62dec4cda91de6eeec21ccf3bfe6dd8d4436a7");
Head.create("PlayerName");
Head.create(UUID);
Head.create(OfflinePlayer);
```

# Maven
```xml
<repositories>
    <repository>
        <id>scarsz</id>
        <url>https://nexus.scarsz.me/content/groups/public/</url>
    </repository>
</repositories>

<dependency>
  <groupId>github.scarsz</groupId>
  <artifactId>mojang</artifactId>
  <version>1.1.1</version>
</dependency>
```
