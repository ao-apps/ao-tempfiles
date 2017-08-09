/*
 * ao-tempfiles - Java temporary file API filling-in JDK gaps and deficiencies.
 * Copyright (C) 2017  AO Industries, Inc.
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

	TempFile(Long contextId, File file) {
		this.contextId = contextId;
		this.file = new AtomicReference<File>(file);
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
			if(f.exists() && !f.delete()) {
				throw new IOException("Unable to delete temporary file: " + f);
			}
		}
	}
}
