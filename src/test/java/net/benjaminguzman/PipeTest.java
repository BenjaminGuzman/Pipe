/*
 * MIT License
 *
 * Copyright (c) 2021. Benjamín Antonio Velasco Guzmán
 * Author: Benjamín Antonio Velasco Guzmán <bg@benjaminguzman.dev>
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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class PipeTest {
	@Test
	@DisplayName("Testing builder setters and getters")
	void builder() {
		PipedOutputStream pipedOutputStream = new PipedOutputStream();
		PipedInputStream pipedInputStream = new PipedInputStream();

		Consumer<? super Exception> onException = Throwable::printStackTrace;
		HashMap<Pattern, Consumer<String>> map = new HashMap<>();

		Pipe.Builder builder = new Pipe.Builder(pipedInputStream, pipedOutputStream)
			.setHeader("Header")
			.setFooter("Footer")
			.setPrefix("Prefix")
			.setSuffix("Suffix")
			.setOnException(onException)
			.setHooks(map)
			.setCloseOutStream(false)
			.setAutoFlush(false);

		assertEquals("Header", builder.getHeader());
		assertEquals("Footer", builder.getFooter());
		assertEquals("Prefix", builder.getPrefix());
		assertEquals("Suffix", builder.getSuffix());
		assertEquals(onException, builder.getOnException());
		assertEquals(map, builder.getHooks());
		assertFalse(builder.shouldCloseOutStream());
		assertFalse(builder.shouldAutoFlush());

		assertTrue(builder.setCloseOutStream(true).shouldCloseOutStream());
		assertTrue(builder.setAutoFlush(true).shouldAutoFlush());
	}

	@Test
	@DisplayName("Testing input is piped correctly to output with all other capabilities (hooks, prefix/suffix...)")
	void pipe() throws IOException, InterruptedException {
		AtomicInteger i = new AtomicInteger(0); // should keep count of the hooks invocations

		// configure hooks
		HashMap<Pattern, Consumer<String>> map = new HashMap<>();
		map.put(Pattern.compile("Super test"), s -> {
			assertTrue(s.contains("Super test"));
			i.incrementAndGet();
		});
		map.put(
			Pattern.compile("Service is (up|running)"),
			s -> {
				assertTrue(s.contains("Service is up") || s.contains("Service is running"));
				i.incrementAndGet();
			}
		);

		// start a new process to print something to its own stdout
		String echoString = "Super test and Service is running and bla bla bla";
		Process proc = new ProcessBuilder("echo", echoString).start();

		ByteArrayOutputStream outStream = new ByteArrayOutputStream();

		// initialize the pipe
		Pipe.Builder builder = new Pipe.Builder(proc.getInputStream(), outStream)
			.setHeader("-- Header --\n")
			.setFooter("-- Footer --\n")
			.setPrefix("Prefix ")
			.setSuffix(" Suffix")
			.setOnException(e -> System.out.println(e.getMessage()))
			.setHooks(map)
			.setCloseOutStream(true)
			.setAutoFlush(false);
		Pipe pipe = new Pipe(builder);

		// start piping
		Thread t = pipe.initThread();
		t.start();
		t.join(); // wait until pipe has ended (no more data in input stream)

		assertEquals(2, i.get());
		assertEquals(
			builder.getHeader()
				+ builder.getPrefix() + echoString + builder.getSuffix() + System.lineSeparator()
				+ builder.getFooter(),
			outStream.toString()
		);
	}

	@Test
	@DisplayName("Testing onException is being executed")
	void onExceptionTest() throws IOException, InterruptedException {
		// start a new process to print something to its own stdout
		String echoString = "Super test and Service is running and bla bla bla";
		Process proc = new ProcessBuilder("echo", echoString).start();

		// immediately close the pipe
		// should provoke an exception when trying to read from it
		proc.getInputStream().close();

		ByteArrayOutputStream outStream = new ByteArrayOutputStream();

		// initialize the pipe
		Pipe pipe = new Pipe(
			new Pipe.Builder(proc.getInputStream(), outStream)
				.setOnException(e -> assertEquals("Stream closed", e.getMessage()))
		);

		// start piping
		pipe.run();
		Thread t = pipe.initThread();
		t.start();
		t.join(); // wait until pipe has ended (no more data in input stream)
	}
}