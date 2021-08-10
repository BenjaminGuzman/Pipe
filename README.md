# Pipe
Simple java utility to act as a pipe (redirect input to output) with extended capabilities such as:

- Callback execution when pattern is found in input.
  You can configure a text pattern and a callback to be executed when
  a such pattern is found within any text line inside the pipe
- Suffix/prefix addition per line.
  For each piped line, you can add a prefix or suffix
- Header/footer addition
  Before or after text is piped, you can print a header or footer

Check the examples below

## Usage

## Maven

Add the maven dependency

```xml
<dependency>
    <groupId>net.benjaminguzman</groupId>
    <artifactId>Pipe</artifactId>
    <version>1.0.2</version>
</dependency>
```

## Code

Usage is very easy:

```Java
Process proc = new ProcessBuilder("echo", "Command to run a process").start();

// initialize the pipe
Pipe pipe = new Pipe(
    new Pipe.Builder(proc.getInputStream(), System.out)
        .setPrefix("[Process]: ")
        .setHeader("--- BEGIN Process execution ---\n")
        .setFooter("--- END process execution ---\n")
        .setCloseOutStream(false) // if true System.out will be closed after pipe is finished
);

// start piping, thread is not needed but recommended, use pipe.run() to run without a thread
Thread t = pipe.initThread();
t.start();
```

Examples here use `Process` class, but you can use `Pipe` in other contexts

### Full code example

```Java
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class Example {
  public static void main(String... args) throws IOException, InterruptedException {
    CountDownLatch latch = new CountDownLatch(1); // latch to wait for the service to start
    HashMap<Pattern, Consumer<String>> map = new HashMap<>();
    map.put(
            Pattern.compile("Service is (up|running)"), // pattern to search (must be simple)
            s -> latch.countDown() // callback to execute when the pattern is found
    );

    // simulate a process start
    String echoString = "Starting service...\n" +
            "Configuration loaded\n" +
            "Starting something else inside service\n" +
            "Starting another thing or dependency\n" +
            "Service is running on 127.0.0.1:1111";
    Process proc = new ProcessBuilder("echo", "-e", echoString).start();

    // initialize the pipe
    Pipe pipe = new Pipe(
            new Pipe.Builder(proc.getInputStream(), System.out)
                    .setPrefix("[Service]: ")
                    .setHeader("--- BEGIN Service startup ---\n")
                    .setFooter("--- END Service startup (output was closed) ---\n")
                    .setHooks(map)
                    .setCloseOutStream(false) // if true System.out will be closed after pipe is finished
    );

    // start piping, thread is not needed but recommended, use pipe.run() to run without a thread
    Thread t = pipe.initThread();
    t.start();

    latch.await(); // wait until the latch is countdown (when pattern is found, see above)
    System.out.println("Service has started. It is time to run something else");

    t.join(); // wait for a clean shutdown
  }
}
```

You can find this full example in
[src/test/java/net/benjaminguzman/Example.java](src/test/java/net/benjaminguzman/Example.java)

Output

```text
--- BEGIN Service startup ---
[Service]: Starting service...
[Service]: Configuration loaded
[Service]: Starting something else inside service
[Service]: Starting another thing or dependency
[Service]: Service is running on 127.0.0.1:1111
Service has started. It is time to run something else
--- END Service startup (output was closed)---
```

Note that the output is NOT

```text
--- BEGIN Service startup ---
[Service]: Starting service...
[Service]: Configuration loaded
[Service]: Starting something else inside service
[Service]: Starting another thing or dependency
[Service]: Service is running on 127.0.0.1:1111
--- END Service startup (output was closed)---
Service has started. It is time to run something else
```

Because hooks (consumers) are executed as soon as the pattern is found

## Test

Simply run

```shell
mvn clean test
```

to run tests

## License

[MIT license](./LICENSE)

Copyright © 2021 Benjamín Antonio Velasco Guzmán