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
