/*
 * ao-tempfiles - Java temporary file API filling-in JDK gaps and deficiencies.
 * Copyright (C) 2017, 2018, 2019, 2020, 2021, 2022  AO Industries, Inc.
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
 * along with ao-tempfiles.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.aoapps.tempfiles;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * the last instance is closed.
 * <p>
 * Thread-safe with fine-grained locking.
 * </p>
 */
public class TempFileContext implements Closeable {

  private static final Logger logger = Logger.getLogger(TempFileContext.class.getName());

  /**
   * The minimum prefix length required by {@link Files#createTempFile(java.lang.String, java.lang.String, java.nio.file.attribute.FileAttribute...)}.
   */
  private static final int MIN_PREFIX_LENGTH = 3;

  /**
   * Any prefix longer than this will be truncated
   */
  private static final int MAX_PREFIX_LENGTH = 64;

  /**
   * The number of active instances is tracked, will remove shutdown hook when gets to zero.
   */
  private static final AtomicInteger activeCount = new AtomicInteger();

  private static class DeleteMe {
    private final File file;
    private final boolean isDirectory;
    private DeleteMe(File file, boolean isDirectory) {
      this.file = file;
      this.isDirectory = isDirectory;
    }
  }

  /**
   * The files registered for delete on exit.
   * <p>
   * Note: The key is an incrementing Long to avoid a reference to the specific instance.
   * </p>
   */
  private static final ConcurrentMap<Long, Map<String, DeleteMe>> deleteOnExits = new ConcurrentHashMap<>();

  /**
   * The shutdown hook shared by all active instances.
   */
  private static volatile Thread shutdownHook;

  private static final AtomicReference<File> systemTmpDir = new AtomicReference<>();
  private static File getSystemTmpDir() {
    File tmpDir = systemTmpDir.get();
    if (tmpDir == null) {
      tmpDir = new File(System.getProperty("java.io.tmpdir"));
      if (systemTmpDir.compareAndSet(null, tmpDir)) {
        // Create if does not exist
        if (!tmpDir.exists()) {
          try {
            Files.createDirectories(tmpDir.toPath());
          } catch (IOException e) {
            throw new UncheckedIOException("System temp directory does not exist and cannot be created: " + tmpDir, e);
          }
        } else {
          // Sanity check
          if (!tmpDir.exists()) {
            throw new UncheckedIOException(new IOException("System temp directory does not exist: " + tmpDir));
          }
          if (!tmpDir.isDirectory()) {
            throw new UncheckedIOException(new IOException("System temp directory is not a directory: " + tmpDir));
          }
          if (!tmpDir.canWrite()) {
            throw new UncheckedIOException(new IOException("System temp directory is not writable: " + tmpDir));
          }
          if (!tmpDir.canRead()) {
            throw new UncheckedIOException(new IOException("System temp directory is not readable: " + tmpDir));
          }
        }
      } else {
        // Another thread already set
        tmpDir = systemTmpDir.get();
      }
    }
    return tmpDir;
  }

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
   * when done with the instance.  This should be done in a try-with-resources, try-finally, or strong
   * equivalent, such as <code>Servlet.destroy()</code>.
   * </p>
   * <p>
   * Shutdown hooks are shared between instances.
   * </p>
   *
   * @param  tmpDir  The temporary directory or {@code null} to use the system default
   *
   * @see  #close()
   */
  @SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
  public TempFileContext(File tmpDir) {
    // Not worth the overhead to check here, since some contexts are very short-lived
    // and any problems will manifest themselves clearly when creating a temporary file.
    // if (!tmpDir.exists()) {
    //    throw new IllegalArgumentException("tmpDir does not exist: " + tmpDir);
    // }
    // if (!tmpDir.isDirectory()) {
    //    throw new IllegalArgumentException("tmpDir is not a directory: " + tmpDir);
    // }
    // if (!tmpDir.canWrite()) {
    //    throw new IllegalArgumentException("tmpDir is not writable: " + tmpDir);
    // }
    // if (!tmpDir.canRead()) {
    //    throw new IllegalArgumentException("tmpDir is not readable: " + tmpDir);
    // }
    this.tmpDir = (tmpDir == null) ? getSystemTmpDir() : tmpDir;
    // Increment activeCount while looking for wraparound
    assert activeCount.get() >= 0;
    int newActiveCount = activeCount.incrementAndGet();
    if (newActiveCount < 0) {
      activeCount.decrementAndGet();
      throw new IllegalStateException("activeCount integer wraparound detected");
    }
    if (logger.isLoggable(Level.FINER)) {
      logger.log(Level.FINER, "activeCount={0}", newActiveCount);
    }
    if (newActiveCount == 1) {
      // Create shutdown hook on first only
      if (logger.isLoggable(Level.FINE)) {
        logger.log(Level.FINE, "Registering shutdown hook");
      }
      shutdownHook = new Thread(() -> {
        for (Map<String, DeleteMe> deleteMap : deleteOnExits.values()) {
          synchronized (deleteMap) {
            for (DeleteMe deleteMe : deleteMap.values()) {
              File f = deleteMe.file;
              boolean isDirectory = deleteMe.isDirectory;
              try {
                if (f.exists()) {
                  if (isDirectory) {
                    TempFile.deleteRecursive(f);
                  } else {
                    Files.delete(f.toPath());
                  }
                }
              } catch (Throwable t) {
                if (logger.isLoggable(Level.WARNING)) {
                  logger.log(Level.WARNING, "Unable to delete " + (isDirectory ? "directory" : "file") + " on shutdown: " + f, t);
                }
              }
            }
          }
        }
      });
      try {
        Runtime.getRuntime().addShutdownHook(shutdownHook);
      } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
        if (logger.isLoggable(Level.WARNING)) {
          logger.log(Level.WARNING, "Failed to add shutdown hook", e);
        }
      }
    }
  }

  /**
   * Uses the provided temporary directory.
   *
   * @param  tmpDir  The temporary directory or {@code null} to use the system default
   *
   * @see  #TempFileContext(java.io.File)
   */
  public TempFileContext(String tmpDir) {
    this((tmpDir == null) ? null : new File(tmpDir));
  }

  /**
   * Uses the system default temporary directory from {@link System#getProperty(java.lang.String) system property} {@code "java.io.tmpdir"}.
   *
   * @see  #getSystemTmpDir()
   * @see  #TempFileContext(java.io.File)
   */
  public TempFileContext() {
    this((File)null);
  }

  /**
   * Gets the temporary directory this instance is using.
   */
  public File getTmpDir() {
    return tmpDir;
  }

  /**
   * Generates a temp file prefix given a name template.
   * <ol>
   * <li>If {@code null} or {@link String#isEmpty()}, {@code "tmp_"} is used.</li>
   * <li>Any character that is not in {@code [a-zA-Z0-9.-_]} is converted to {@code '_'}.</li>
   * <li>If less than {@link #MIN_PREFIX_LENGTH}, padded with trailing {@code '_'} to a length of {@link #MIN_PREFIX_LENGTH}.</li>
   * <li>If greater than {@link #MAX_PREFIX_LENGTH} characters, is truncated to a length of {@link #MAX_PREFIX_LENGTH}.</li>
   * </ol>
   *
   * @param  template  the name of the temporary file that will be filtered for only safe characters
   */
  public static String generatePrefix(String template) {
    if (template == null || template.isEmpty()) {
      return "tmp_";
    } else {
      int len = Math.min(template.length(), MAX_PREFIX_LENGTH);
      // Replace characters that are not safe
      StringBuilder prefix = new StringBuilder(Math.min(len, MIN_PREFIX_LENGTH));
      for (int i = 0; i < len; i++) {
        char ch = template.charAt(i);
        prefix.append(
          (ch >= 'a' && ch <= 'z')
          || (ch >= 'A' && ch <= 'Z')
          || (ch >= '0' && ch <= '9')
          || ch == '.'
          || ch == '-'
          || ch == '_'
          ? ch
          : '_'
        );
      }
      while (prefix.length() < MIN_PREFIX_LENGTH) {
        prefix.append('_');
      }
      return prefix.toString();
    }
  }

  private static String formatPrefix(String prefix) {
    if (prefix == null || prefix.isEmpty()) {
      prefix = "tmp_";
    } else {
      int plen = prefix.length();
      if (plen > MAX_PREFIX_LENGTH) {
        prefix = prefix.substring(0, MAX_PREFIX_LENGTH);
      } else if (plen < MIN_PREFIX_LENGTH) {
        StringBuilder sb = new StringBuilder(MIN_PREFIX_LENGTH);
        sb.append(prefix);
        do {
          sb.append('_');
        } while (sb.length() < MIN_PREFIX_LENGTH);
        prefix = sb.toString();
      }
    }
    assert prefix.length() >= MIN_PREFIX_LENGTH;
    assert prefix.length() <= MAX_PREFIX_LENGTH;
    return prefix;
  }

  /**
   * Creates a new temporary directory with the given prefix, recursively deleting on close or exit.
   *
   * @param  prefix  If {@code null} or {@link String#isEmpty()}, {@code "tmp_"} is used.
   *                 If less than {@link #MIN_PREFIX_LENGTH}, padded with trailing {@code '_'} to a length of {@link #MIN_PREFIX_LENGTH}.
   *                 If greater than {@link #MAX_PREFIX_LENGTH} characters, is truncated to a length of {@link #MAX_PREFIX_LENGTH}.
   *
   * @throws  IllegalStateException  if already {@link #close() closed}
   */
  public TempFile createTempDirectory(String prefix) throws IllegalStateException, IOException {
    if (closed.get()) {
      throw new IllegalStateException("TempFiles is closed");
    }
    while (true) {
      Path tmpPath = Files.createTempDirectory(tmpDir.toPath(), formatPrefix(prefix));
      File tmpFile = tmpPath.toFile();
      if (addDeleteOnExit(id, tmpFile, true)) {
        return new TempFile(id, tmpFile, true);
      }
      Files.delete(tmpPath);
    }
  }

  /**
   * Creates a new temporary directory with default prefix, recursively deleting on close or exit.
   *
   * @throws  IllegalStateException  if already {@link #close() closed}
   */
  public TempFile createTempDirectory() throws IllegalStateException, IOException {
    return createTempDirectory(null);
  }

  /**
   * Creates a new temporary file with the given prefix and suffix, deleting on close or exit.
   *
   * @param  prefix  If {@code null} or {@link String#isEmpty()}, {@code "tmp_"} is used.
   *                 If less than {@link #MIN_PREFIX_LENGTH}, padded with trailing {@code '_'} to a length of {@link #MIN_PREFIX_LENGTH}.
   *                 If greater than {@link #MAX_PREFIX_LENGTH} characters, is truncated to a length of {@link #MAX_PREFIX_LENGTH}.
   *
   * @param  suffix  when {@code null}, {@code ".tmp"} is used.
   *
   * @throws  IllegalStateException  if already {@link #close() closed}
   */
  public TempFile createTempFile(String prefix, String suffix) throws IllegalStateException, IOException {
    if (closed.get()) {
      throw new IllegalStateException("TempFiles is closed");
    }
    while (true) {
      Path tmpPath = Files.createTempFile(tmpDir.toPath(), formatPrefix(prefix), suffix);
      File tmpFile = tmpPath.toFile();
      if (addDeleteOnExit(id, tmpFile, false)) {
        return new TempFile(id, tmpFile, false);
      }
      Files.delete(tmpPath);
    }
  }

  /**
   * Creates a new temporary file based on the given name, deleting on close or exit.
   * <p>
   * When the file contains a dot ('.') followed by only the characters in {@code [a-zA-Z0-9_]}, and the dot is not
   * the first character, it is used as the temp file suffix (to maintain file type by extension).  Multiple
   * extensions are supported, such as used by {@code "filename.tar.gz"}.
   * </p>
   * <p>
   * When the suffix is removed from the name, the prefix is followed by an underscore ('_') separator.
   * </p>
   * <p>
   * When no automatic suffix is determine, defaults to {@code ".tmp"}.
   * </p>
   *
   * @throws  IllegalStateException  if already {@link #close() closed}
   */
  public TempFile createTempFile(String name) throws IllegalStateException, IOException {
    String prefix;
    String suffix;
    if (name == null || name.isEmpty()) {
      prefix = null;
      suffix = null;
    } else {
      int len = name.length();
      int lastDot = len;
      for (int i = len - 1; i > 0; i--) {
        char ch = name.charAt(i);
        if (ch == '.') {
          if (i == (lastDot - 1)) {
            // End on double-dot
            break;
          }
          lastDot = i;
        } else if (
          (ch < 'a' || ch > 'z')
          && (ch < 'A' || ch > 'Z')
          && (ch < '0' || ch > '9')
          && ch != '_'
        ) {
          // End on unsupported character
          break;
        }
      }
      if (lastDot == len) {
        prefix = name;
        suffix = null;
      } else {
        prefix = name.substring(lastDot) + '_';
        suffix = name.substring(lastDot);
      }
    }
    return createTempFile(prefix, suffix);
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
   * @return  {@code true} when added or {@code false} when name already tracked within the id
   */
  private static boolean addDeleteOnExit(Long id, File tmpFile, boolean isDirectory) {
    Map<String, DeleteMe> deleteMap = deleteOnExits.get(id);
    if (deleteMap == null) {
      deleteMap = new LinkedHashMap<>();
      Map<String, DeleteMe> existing = deleteOnExits.putIfAbsent(id, deleteMap);
      if (existing != null) {
        deleteMap = existing;
      }
    }
    synchronized (deleteMap) {
      return deleteMap.putIfAbsent(tmpFile.getName(), new DeleteMe(tmpFile, isDirectory)) == null;
    }
  }

  /**
   * @see  TempFile#close()
   */
  static void removeDeleteOnExit(Long id, String name) {
    Map<String, DeleteMe> deleteMap = deleteOnExits.get(id);
    if (deleteMap != null) {
      synchronized (deleteMap) {
        deleteMap.remove(name);
      }
    }
  }

  /**
   * Gets the number of files that are currently scheduled to be deleted on close/exit.
   */
  public int getSize() {
    Map<String, DeleteMe> deleteMap = deleteOnExits.get(id);
    if (deleteMap != null) {
      synchronized (deleteMap) {
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
   * If already closed, no action will be taken and no exception thrown.
   * </p>
   */
  @Override
  @SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
  public void close() throws IOException {
    boolean alreadyClosed = closed.getAndSet(true);
    if (!alreadyClosed) {
      Map<String, DeleteMe> deleteMap = deleteOnExits.remove(id);
      assert activeCount.get() > 0;
      int newActiveCount = activeCount.decrementAndGet();
      if (logger.isLoggable(Level.FINER)) {
        logger.log(Level.FINER, "activeCount={0}", newActiveCount);
      }
      if (newActiveCount == 0) {
        Thread hook = shutdownHook;
        assert hook != null;
        shutdownHook = null;
        if (logger.isLoggable(Level.FINE)) {
          logger.log(Level.FINE, "Removing shutdown hook");
        }
        try {
          Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException e) {
          // System shutting down, can't remove hook
        } catch (SecurityException e) {
          if (logger.isLoggable(Level.WARNING)) {
            logger.log(Level.WARNING, "Failed to removing shutdown hook", e);
          }
        }
      }
      // Delete own temp files
      if (deleteMap != null) {
        List<DeleteMe> failedDelete = null;
        List<Throwable> causes = null;
        synchronized (deleteMap) {
          for (DeleteMe deleteMe : deleteMap.values()) {
            File f = deleteMe.file;
            try {
              if (f.exists()) {
                if (deleteMe.isDirectory) {
                  TempFile.deleteRecursive(f);
                } else {
                  Files.delete(f.toPath());
                }
              }
            } catch (Throwable t) {
              if (failedDelete == null) {
                failedDelete = new ArrayList<>();
                causes = new ArrayList<>();
              }
              assert causes != null;
              failedDelete.add(deleteMe);
              causes.add(t);
            }
          }
        }
        if (failedDelete != null) {
          assert causes != null;
          if (failedDelete.size() == 1) {
            DeleteMe failed = failedDelete.get(0);
            throw new IOException("Unable to delete temporary " + (failed.isDirectory ? "directory" : "file") + ": " + failed.file, causes.get(0));
          } else {
            StringBuilder sb = new StringBuilder("Unable to delete temporary directories/files:");
            for (DeleteMe failed : failedDelete) {
              sb.append("\n    ").append(failed.file);
            }
            IOException ioExc = new IOException(sb.toString());
            for (Throwable cause : causes) {
              ioExc.addSuppressed(cause);
            }
            throw ioExc;
          }
        }
      }
    }
  }
}
