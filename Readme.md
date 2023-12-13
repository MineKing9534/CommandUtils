![[Java CI]](https://github.com/MineKing9534/CommandUtils/actions/workflows/check.yml/badge.svg)
![[Latest Version]](https://maven.mineking.dev/api/badge/latest/releases/de/mineking/CommandUtils?prefix=v&name=Latest%20Version)

# Installation

CommandUtils is hosted on a custom repository at [https://maven.mineking.dev](https://maven.mineking.dev/releases/de/mineking/CommandUtils). Replace VERSION with the lastest version (without the `v` prefix).
Alternatively, you can download the artifacts from jitpack (not recommended).

### Gradle

```groovy
repositories {
  maven { url "https://maven.mineking.dev/releases" }
}

dependencies {
  implementation "de.mineking:CommandUtils:1.0.0"
}
```

### Maven

```xml
<repositories>
  <repository>
    <id>mineking</id>
    <url>https://maven.mineking.dev/releases</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>de.mineking</groupId>
    <artifactId>CommandUtils</artifactId>
    <version>1.0.0</version>
  </dependency>
</dependencies>
```