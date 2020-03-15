/*
 * ao-tempfiles - Java temporary file API filling-in JDK gaps and deficiencies.
 * Copyright (C) 2017, 2018, 2019, 2020  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of ao-tempfiles.
 *
 * ao-tempfiles is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ao-tempfiles is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ao-tempfiles.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.tempfiles;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides a central temporary file manager for use by any number of projects.
 * The temporary files are optionally deleted on shutdown using shutdown hooks.
 * The shutdown-registered temporary files are also immediately deleted when
 * the last instance is disposed.
 * <p>
 * Thread-safe with fine-grained locking.
 * </p>
 */
public class TempFileContext implements Closeable {

	private static final Logger logger = Logger.getLogger(TempFileContext.class.getName());

	/**
	 * The number of active instances is tracked, will remove shutdown hook when gets to zero.
	 */
	private static final AtomicInteger activeCount = new AtomicInteger();

	/**
	 * The files registered for delete on exit.
	 * <p>
	 * Note: The key is an incrementing Long to avoid a reference to the specific instance.
	 * This allows {@link #finalize()} to kick-in and automatically call {@link #close()}
	 * when API users are reckless.
	 * </p>
	 */
	private static final ConcurrentMap<Long, Map<String,File>> deleteOnExits = new ConcurrentHashMap<>();

	/**
	 * The shutdown hook shared by all active instances.
	 */
	private static volatile Thread shutdownHook;

	/**
	 * Unique ID generator.
	 */
	private static final AtomicLong idGenerator = new AtomicLong(1);

	/**
	 * The unique ID for this instance.
	 */
	private final Long id = idGenerator.getAndIncrement();

	/**
	 * The directory containing the temporary files for this instance.
	 */
	private final File tmpDir;

	/**
	 * Set to true when closed.
	 */
	private final AtomicBoolean closed = new AtomicBoolean();

	/**
	 * <p>
	 * Create a new instance of the temp file manager.  {@link #close()} must be called
	 * when done with the instance.  This should be done in a try-finally or strong
	 * equivalent, such as <code>Servlet.destroy()</code>.
	 * </p>
	 * <p>
	 * Shutdown hooks are shared between instances.
	 * </p>
	 *
	 * @see  #close()
	 */
	public TempFileContext(File tmpDir) {
		// Not worth the overhead to check here, since some contexts are very short-lived
		// and any problems will manifest themselves clearly when creating a temporary file.
		// if(!tmpDir.exists()) throw new IllegalArgumentException("tmpDir does not exist: " + tmpDir);
		// if(!tmpDir.isDirectory()) throw new IllegalArgumentException("tmpDir is not a directory: " + tmpDir);
		// if(!tmpDir.canWrite()) throw new IllegalArgumentException("tmpDir is not writable: " + tmpDir);
		// if(!tmpDir.canRead()) throw new IllegalArgumentException("tmpDir is not readable: " + tmpDir);
		this.tmpDir = tmpDir;
		// Increment activeCount while looking for wraparound
		assert activeCount.get() >= 0;
		int newActiveCount = activeCount.incrementAndGet();
		if(newActiveCount < 0) {
			activeCount.decrementAndGet();
			throw new IllegalStateException("activeCount integer wraparound detected");
		}
		if(logger.isLoggable(Level.FINER)) logger.log(Level.FINER, "activeCount={0}", newActiveCount);
		if(newActiveCount == 1) {
			// Create shutdown hook on first only
			if(logger.isLoggable(Level.FINE)) logger.log(Level.FINE, "Registering shutdown hook");
			shutdownHook = new Thread() {
				@Override
				public void run() {
					for(Map<String,File> deleteMap : deleteOnExits.values()) {
						synchronized(deleteMap) {
							for(File file : deleteMap.values()) {
								if(file.exists() && !file.delete()) {
									if(logger.isLoggable(Level.WARNING)) logger.log(Level.WARNING, "Unable to delete file on shutdown: {0}", file);
								}
							}
						}
					}
				}
			};
			try {
				Runtime.getRuntime().addShutdownHook(shutdownHook);
			} catch(IllegalArgumentException | IllegalStateException | SecurityException e) {
				if(logger.isLoggable(Level.WARNING)) logger.log(Level.WARNING, "Failed to add shutdown hook", e);
			}
		}
	}

	/**
	 * Uses the provided temporary directory.
	 *
	 * @see  #TempFileContext(java.io.File)
	 */
	public TempFileContext(String tmpDir) {
		// TODO: How expensive is new File with its normalization?
		//       Would it be worth a ConcurrentMap to avoid this?
		this(new File(tmpDir));
	}

	private static final AtomicReference<File> systemTmpDir = new AtomicReference<>();
	private static File getSystemTmpDir() {
		File tmpDir = systemTmpDir.get();
		if(tmpDir == null) {
			tmpDir = new File(System.getProperty("java.io.tmpdir"));
			File existing = systemTmpDir.getAndSet(tmpDir);
			if(existing != null) tmpDir = existing;
		}
		return tmpDir;
	}

	/**
	 * Uses the system default temporary directory from {@link System#getProperty(java.lang.String) system property} {@code "java.io.tmpdir"}.
	 *
	 * @see  #getSystemTmpDir()
	 * @see  #TempFileContext(java.io.File)
	 */
	public TempFileContext() {
		this(getSystemTmpDir());
	}

	/**
	 * Gets the temporary directory this instance is using.
	 */
	public File getTmpDir() {
		return tmpDir;
	}

	/**
	 * Creates a new temporary file with the given prefix and suffix, deleting on close or exit.
	 *
	 * @param  prefix  if {@code null}, {@code "tmp_"} is used.
	 *                 If less than three characters, padded with trailing {@code '_'} to three characters.
	 *
	 * @param  suffix  when {@code null}, {@code ".tmp"} is used.
	 *
	 * @throws  IllegalStateException  if already {@link #close() closed}
	 */
	public TempFile createTempFile(String prefix, String suffix) throws IllegalStateException, IOException {
		if(closed.get()) throw new IllegalStateException("TempFiles is closed");

		if(prefix == null) {
			prefix = "tmp_";
		} else {
			while(prefix.length() < 3) prefix += '_';
		}
		File tmpFile = File.createTempFile(prefix, suffix, tmpDir);
		// Add to delete-on-exit
		Map<String,File> deleteMap = deleteOnExits.get(id);
		if(deleteMap == null) {
			deleteMap = new LinkedHashMap<>();
			Map<String,File> existing = deleteOnExits.putIfAbsent(id, deleteMap);
			if(existing != null) deleteMap = existing;
		}
		synchronized(deleteMap) {
			if(deleteMap.put(tmpFile.getName(), tmpFile) != null) throw new IOException("Duplicate temp filename: " + tmpFile);
		}
		// Return temp file
		return new TempFile(id, tmpFile);
	}

	/**
	 * Creates a new temporary file with default suffix, deleting on close or exit.
	 *
	 * @throws  IllegalStateException  if already {@link #close() closed}
	 */
	public TempFile createTempFile(String prefix) throws IllegalStateException, IOException {
		return createTempFile(prefix, null);
	}

	/**
	 * Creates a new temporary file with default prefix and suffix, deleting on close or exit.
	 *
	 * @throws  IllegalStateException  if already {@link #close() closed}
	 */
	public TempFile createTempFile() throws IllegalStateException, IOException {
		return createTempFile(null, null);
	}

	/**
	 * @see  TempFile#close()
	 */
	static void removeDeleteOnExit(Long id, String name) {
		Map<String,File> deleteMap = deleteOnExits.get(id);
		if(deleteMap != null) {
			synchronized(deleteMap) {
				deleteMap.remove(name);
			}
		}
	}

	/**
	 * Gets the number of files that are currently scheduled to be deleted on close/exit.
	 */
	public int getSize() {
		Map<String,File> deleteMap = deleteOnExits.get(id);
		if(deleteMap != null) {
			synchronized(deleteMap) {
				return deleteMap.size();
			}
		} else {
			return 0;
		}
	}

	/**
	 * <p>
	 * Closes this instance.  Once closed, no additional temp files may be managed.
	 * Any overriding method must call super.close().
	 * </p>
	 * <p>
	 * If this is the last active instance, the underlying shutdown hook is also removed.
	 * </p>
	 * <p>
	 * If already disposed, no action will be taken and no exception thrown.
	 * </p>
	 */
	@Override
	public void close() throws IOException {
		boolean alreadyClosed = closed.getAndSet(true);
		if(!alreadyClosed) {
			Map<String,File> deleteMap = deleteOnExits.remove(id);
			assert activeCount.get() > 0;
			int newActiveCount = activeCount.decrementAndGet();
			if(logger.isLoggable(Level.FINER)) logger.log(Level.FINER, "activeCount={0}", newActiveCount);
			if(newActiveCount == 0) {
				Thread hook = shutdownHook;
				assert hook != null;
				shutdownHook = null;
				if(logger.isLoggable(Level.FINE)) logger.log(Level.FINE, "Removing shutdown hook");
				try {
					Runtime.getRuntime().removeShutdownHook(hook);
				} catch(IllegalStateException e) {
					// System shutting down, can't remove hook
				} catch(SecurityException e) {
					if(logger.isLoggable(Level.WARNING)) logger.log(Level.WARNING, "Failed to removing shutdown hook", e);
				}
			}
			// Delete own temp files
			if(deleteMap != null) {
				Set<File> failedDelete = null;
				synchronized(deleteMap) {
					for(File file : deleteMap.values()) {
						if(file.exists() && !file.delete()) {
							if(failedDelete == null) failedDelete = new LinkedHashSet<>();
							failedDelete.add(file);
						}
					}
				}
				if(failedDelete != null) {
					if(failedDelete.size() == 1) {
						throw new IOException("Unable to delete temporary file: " + failedDelete.iterator().next());
					} else {
						StringBuilder sb = new StringBuilder("Unable to delete temporary files:");
						for(File file : failedDelete) {
							sb.append("\n    ").append(file.toString());
						}
						throw new IOException(sb.toString());
					}
				}
			}
		}
	}

	/**
	 * Do not rely on the finalizer - this is just in case something is way off
	 * and the calling code doesn't correctly dispose their instances.
	 */
	@Override
	protected void finalize() throws Throwable {
		try {
			close();
		} finally {
			super.finalize();
		}
	}
}
