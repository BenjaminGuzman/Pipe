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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class Pipe implements Runnable {
	@NotNull
	private final Builder options;

	public Pipe(@NotNull Builder options) {
		this.options = options;
	}

	/**
	 * Run inside a separate thread
	 * <p>
	 * This will read from the configured input stream and write to the given output stream
	 * <p>
	 * All of that will be done using configurations set in {@link Builder}
	 * <p>
	 * Read and write are both done in the same thread
	 * <p>
	 * Execution terminates when there is no more data in input stream or an exception occurs
	 *
	 * @see #initThread()
	 */
	@Override
	public void run() {
		final BufferedWriter writer = new BufferedWriter(
			new OutputStreamWriter(options.outStream, options.outCharset)
		);
		try (BufferedReader reader = new BufferedReader(
			new InputStreamReader(options.inStream, options.inCharset)
		)) {
			// write header
			if (options.header != null) writer.write(options.header);

			// just "cache" values to prevent doing this null checks in the while loop
			// (that may be more expensive, because it'll probably be executed a lot of times)
			boolean hasConfiguredHooks = options.hooks != null && !options.hooks.isEmpty();
			boolean hasPrefix = options.prefix != null;
			boolean hasSuffix = options.suffix != null;

			// read from input stream and write to output stream
			String line;
			while ((line = reader.readLine()) != null) {
				if (hasPrefix) writer.write(options.prefix);
				writer.write(line);
				if (hasSuffix) writer.write(options.suffix);
				writer.newLine();

				if (options.autoFlush) writer.flush();

				// check if the line contains one of the specified patterns
				if (hasConfiguredHooks) {
					String finalLine = line;
					options.hooks.forEach((pattern, consumer) -> {
						if (pattern.matcher(finalLine).find())
							consumer.accept(finalLine);
					});
				}
			}

			// write footer
			if (options.footer != null) writer.write(options.footer);
		} catch (IOException e) {
			if (options.onException != null)
				options.onException.accept(e);
		} finally {
			try {
				writer.flush();
				if (options.closeOutStream)
					writer.close();
			} catch (IOException e) {
				if (options.onException != null)
					options.onException.accept(e);
			}
		}
	}

	/**
	 * Initializes a new {@link Thread} that will run {@link #run()} method on start
	 * ({@link Thread#start()}) is not being called here)
	 * <p>
	 * The created thread is daemon and its name is "Pipe-Thread"
	 * <p>
	 * For maximum performance, don't use this method, but instead use an {@link java.util.concurrent.Executor}
	 *
	 * @return the created thread
	 * @see #initThread(String)
	 * @see java.util.concurrent.Executor
	 */
	public Thread initThread() {
		return initThread("Pipe-Thread");
	}

	/**
	 * Initializes a new {@link Thread} that will run {@link #run()} method on start
	 * ({@link Thread#start()}) is not being called here)
	 * <p>
	 * The created thread is daemon
	 * <p>
	 * For maximum performance, don't use this method, but instead use an {@link java.util.concurrent.Executor}
	 *
	 * @param threadName thread name
	 * @return the created thread
	 */
	public Thread initThread(String threadName) {
		Thread t = new Thread(this);
		t.setDaemon(true);
		t.setName(threadName);
		return t;
	}

	public static class Builder {
		@NotNull
		private final InputStream inStream;

		@NotNull
		private final OutputStream outStream;

		@NotNull
		private final Charset inCharset;

		@NotNull
		private final Charset outCharset;

		@Nullable
		private String header;

		@Nullable
		private String footer;

		@Nullable
		private String prefix;

		@Nullable
		private String suffix;

		@Nullable
		private Consumer<? super Exception> onException;

		@Nullable
		private Map<Pattern, Consumer<String>> hooks;

		private boolean closeOutStream = true;
		private boolean autoFlush = true;

		/**
		 * Creates a builder object with {@link StandardCharsets#UTF_8} input and output charset
		 *
		 * @param inStream  Data will be read from this stream
		 * @param outStream Read data will be written in this stream
		 */
		public Builder(@NotNull InputStream inStream, @NotNull OutputStream outStream) {
			this(inStream, outStream, StandardCharsets.UTF_8, StandardCharsets.UTF_8);
		}

		/**
		 * @param inStream   Data will be read from this stream
		 * @param outStream  Read data will be written in this stream
		 * @param inCharset  Data will be read from input stream using this encoding
		 * @param outCharset Data will be written to output stream using this encoding
		 */
		public Builder(
			@NotNull InputStream inStream,
			@NotNull OutputStream outStream,
			@NotNull Charset inCharset,
			@NotNull Charset outCharset
		) {
			this.inStream = inStream;
			this.outStream = outStream;
			this.inCharset = inCharset;
			this.outCharset = outCharset;
		}

		/**
		 * @param should_close Indicates whether the output stream should be closed when the input
		 *                     stream is also closed. Default: true
		 */
		public Builder setCloseOutStream(boolean should_close) {
			this.closeOutStream = should_close;
			return this;
		}

		/**
		 * @param auto_flush If true, the inner {@link BufferedWriter} will be flushed on every new line.
		 *                   If false, it'll be flushed as needed (normally when buffer is full).
		 *                   Default: true
		 */
		public Builder setAutoFlush(boolean auto_flush) {
			this.autoFlush = auto_flush;
			return this;
		}

		@NotNull
		public InputStream getInStream() {
			return inStream;
		}

		@NotNull
		public OutputStream getOutStream() {
			return outStream;
		}

		@NotNull
		public Charset getInCharset() {
			return inCharset;
		}

		@NotNull
		public Charset getOutCharset() {
			return outCharset;
		}

		@Nullable
		public String getHeader() {
			return header;
		}

		/**
		 * @param header string to be added to the beginning of the output. If null, nothing is added.
		 *               It is recommended that this string ends with '\n'
		 */
		public Builder setHeader(@Nullable String header) {
			this.header = header;
			return this;
		}

		@Nullable
		public String getFooter() {
			return footer;
		}

		/**
		 * @param footer string to be added to the end of the output (once the input stream is closed).
		 *               If null, nothing is added.
		 *               It is recommended that this string ends with '\n'
		 */
		public Builder setFooter(@Nullable String footer) {
			this.footer = footer;
			return this;
		}

		@Nullable
		public String getPrefix() {
			return prefix;
		}

		/**
		 * @param prefix prefix to be added to each line of the output.
		 *               It is recommended that this string ends with ' ' (space)
		 */
		public Builder setPrefix(@Nullable String prefix) {
			this.prefix = prefix;
			return this;
		}

		@Nullable
		public String getSuffix() {
			return suffix;
		}

		/**
		 * @param suffix suffix to be added to each line of the output.
		 *               It is recommended that this string starts with ' ' (space)
		 */
		public Builder setSuffix(@Nullable String suffix) {
			this.suffix = suffix;
			return this;
		}

		@Nullable
		public Consumer<? super Exception> getOnException() {
			return onException;
		}

		/**
		 * @param onException Callback to execute when an error occurs while reading from the input stream
		 *                    or writing to the output stream
		 */
		public Builder setOnException(@Nullable Consumer<? super Exception> onException) {
			this.onException = onException;
			return this;
		}

		@Nullable
		public Map<Pattern, Consumer<String>> getHooks() {
			return hooks;
		}

		/**
		 * @param hooks map of patterns and consumers. When a pattern (key) is found within any line of text
		 *              from the input stream, the consumer (value) is called ({@link Consumer#accept(Object)})
		 *              with the argument being the full line of text that triggered its execution.
		 *              <p>
		 *              To prevent performance issues, it is recommended patterns to be very simple.
		 *              It is also recommended not to add too many patterns
		 *              (for every input line it will be checked if it contains the given pattern, and it has
		 *              complexity O(nm) or worse)
		 */
		public Builder setHooks(@Nullable Map<Pattern, Consumer<String>> hooks) {
			this.hooks = hooks;
			return this;
		}

		public boolean shouldCloseOutStream() {
			return closeOutStream;
		}

		public boolean shouldAutoFlush() {
			return autoFlush;
		}
	}
}
