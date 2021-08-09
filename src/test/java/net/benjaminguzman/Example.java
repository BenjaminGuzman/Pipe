/*
 * MIT License
 *
 * Copyright (c) 2021. Benjamín Antonio Velasco Guzmán
 * Author: Benjamín Antonio Velasco Guzmán <***REMOVED***>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.benjaminguzman;

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
/*
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
                .setFooter("--- END Service startup (output was closed)---\n")
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
 */