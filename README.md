# bulldog-example

Template project for tinkering with the [Bulldog GPIO library](https://github.com/SilverThings/bulldog).

## Usage

The project uses Gradle for dependency and build management.

Simply use the Gradle wrapper to compile the sources and produce a runnable JAR file:

```
   $ ./gradlew shadowJar
       ..snip..
       
   $ java -jar build/libs/bulldog-example-all.jar
```

## RTSJ support with JamaicaVM

To compile RTSJ code using the JamaicaVM toolchain, simply add the `-Prtsj` flag
to the Gradle build command:

```
    $ ./gradlew -Prtsj shadowJar
        ..snip..

    $ jamaicavm -jar build/libs/bulldog-example-all.jar
```

If you have installed the JamaicaVM toolchain to a custom location,
simply point the entries in `gradle.properties` to the correct folders.