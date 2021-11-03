/*
 * ao-tempfiles - Java temporary file API filling-in JDK gaps and deficiencies.
 * Copyright (C) 2017, 2019, 2021  AO Industries, Inc.
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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A temporary file that is deleted when {@link #close() closed} or when its
 * associated {@link TempFileContext} is {@link TempFileContext#close() closed}.
 * <p>
 * Thread-safe with fine-grained locking.
 * </p>
 */
public class TempFile implements Closeable {

	private final Long contextId;
	private final AtomicReference<File> file;
	private final boolean isDirectory;

	TempFile(Long contextId, File file, boolean isDirectory) {
		this.contextId = contextId;
		this.file = new AtomicReference<>(file);
		this.isDirectory = isDirectory;
	}

	/**
	 * Gets the temporary file.
	 *
	 * @throws  IllegalStateException  when already closed
	 */
	public File getFile() throws IllegalStateException {
		File f = file.get();
		if(f == null) throw new IllegalStateException("Temp file closed");
		return f;
	}

	// TODO: This could use FileUtils from either ao-lang or commons-io, at the cost of a new dependency
	//
	// Note: This is copied from FileUtils to avoid dependency
	static void deleteRecursive(File file) throws IOException {
		Path deleteMe = file.toPath();
		Files.walkFileTree(
			deleteMe,
			// Java 9: new SimpleFileVisitor<>
			new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				}
				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					if(exc != null) throw exc;
					Files.delete(dir);
					return FileVisitResult.CONTINUE;
				}
			}
		);
		assert !Files.exists(deleteMe, LinkOption.NOFOLLOW_LINKS);
	}

	/**
	 * Closes the temporary file, de-registering from delete on exit and deleting
	 * the underlying file.
	 */
	@Override
	public void close() throws IOException {
		File f = file.getAndSet(null);
		if(f != null) {
			// De-register from shutdown hook
			TempFileContext.removeDeleteOnExit(contextId, f.getName());
			if(f.exists()) {
				if(isDirectory) {
					deleteRecursive(f);
				} else {
					Files.delete(f.toPath());
				}
			}
		}
	}
}
