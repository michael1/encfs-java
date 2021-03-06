/*
 * EncFS Java Library
 * Copyright (C) 2011 
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 */

package org.mrpdaemon.sec.encfs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * Class implementing an EncFSFileProvider for accessing the local filesystem
 * 
 * Note that all path parameters are relative to the rootPath provided to the
 * constructor. Thus, if one instantiates an EncFSFileProvider("/home/jdoe"),
 * the proper way to refer to /home/jdoe/dir/file.ext is by "dir/file.ext".
 */
public class EncFSLocalFileProvider implements EncFSFileProvider {

	/**
	 * Path separator for the local filesystem
	 */
	public final String separator;

	// Root path of this file provider
	private final File rootPath;

	/**
	 * Creates a new EncFSLocalFileProvider
	 * 
	 * @param rootPath
	 *            Root path of the file provider (all other paths will be
	 *            relative to this)
	 */
	public EncFSLocalFileProvider(File rootPath) {
		this.rootPath = rootPath;
		this.separator = File.separator;
	}

	/**
	 * Returns whether the given source path represents a directory in the
	 * underlying filesystem
	 * 
	 * @param srcPath
	 *            Path of the source file or directory
	 * 
	 * @return true if path represents a directory, false otherwise
	 * 
	 * @throws IOException
	 *             Source file/dir doesn't exist or misc. I/O error
	 */
	public boolean isDirectory(String srcPath) {
		File file = new File(rootPath.getAbsoluteFile(), srcPath);
		return file.isDirectory();
	}

	/**
	 * Get a File object representing the given path
	 * 
	 * @param path
	 *            Path of the file or directory
	 * 
	 * @return File object representing the given path
	 */
	public File getFile(String path) {
		File file = new File(rootPath.getAbsoluteFile(), path);
		return file;
	}

	/**
	 * Returns the path separator for the underlying filesystem
	 * 
	 * @return String representing the path separator
	 */
	public final String getSeparator() {
		return separator;
	}

	/**
	 * Returns the root path for the underlying filesystem
	 * 
	 * @return String representing the root path
	 */
	public final String getRootPath() {
		return "/";
	}

	/**
	 * Returns whether the file or directory exists
	 * 
	 * @param srcPath
	 *            Path of the file or directory
	 * 
	 * @return true if file or directory exists, false otherwise
	 * 
	 * @throws IOException
	 *             Misc. I/O error
	 */
	public boolean exists(String srcPath) {
		File file = new File(rootPath.getAbsoluteFile(), srcPath);
		return file.exists();
	}

	/**
	 * Return EncFSFileInfo for the given file or directory
	 * 
	 * @param srcPath
	 *            Path of the file or directory
	 * 
	 * @return EncFSFileInfo for the given file or directory
	 * 
	 * @throws IOException
	 *             Path doesn't exist or misc. I/O error
	 */
	public EncFSFileInfo getFileInfo(String srcPath) {
		File sourceFile = new File(rootPath.getAbsoluteFile(), srcPath);
		return convertToFileInfo(sourceFile);
	}

	/**
	 * Returns the list of files under the given directory path
	 * 
	 * @param dirPath
	 *            Path of the directory to list files from
	 * 
	 * @return a List of EncFSFileInfo representing files under the dir
	 * 
	 * @throws IOException
	 *             Path not a directory or misc. I/O error
	 */
	public List<EncFSFileInfo> listFiles(String dirPath) {
		File srcDir = new File(rootPath.getAbsoluteFile(), dirPath);
		File[] files = srcDir.listFiles();
		List<EncFSFileInfo> results = new ArrayList<EncFSFileInfo>(files.length);
		for (File file : files) {
			results.add(convertToFileInfo(file));
		}
		return results;
	}

	/**
	 * Move a file/directory to a different location
	 * 
	 * @param srcPath
	 *            Path to the source file or directory
	 * @param dstPath
	 *            Path for the destination file or directory
	 * 
	 * @return true if the move is successful, false otherwise
	 * 
	 * @throws IOException
	 *             Source file/dir doesn't exist or misc. I/O error
	 */
	public boolean move(String srcPath, String dstPath) throws IOException {
		File sourceFile = new File(rootPath.getAbsoluteFile(), srcPath);
		File destFile = new File(rootPath.getAbsoluteFile(), dstPath);

		if (!sourceFile.exists()) {
			throw new FileNotFoundException("Path '" + srcPath
					+ "' doesn't exist!");
		}

		return sourceFile.renameTo(destFile);
	}

	/**
	 * Delete the file or directory with the given path
	 * 
	 * @param srcPath
	 *            Path of the source file or directory
	 * 
	 * @return true if deletion is successful, false otherwise
	 * 
	 * @throws IOException
	 *             Source file/dir doesn't exist or misc. I/O error
	 */
	public boolean delete(String srcPath) {
		File toEncFile = new File(rootPath.getAbsoluteFile(), srcPath);
		boolean result = toEncFile.delete();
		return result;
	}

	/**
	 * Create a directory with the given path
	 * 
	 * Note that all path elements except the last one must exist for this
	 * method. If that is not true mkdirs should be used instead
	 * 
	 * @param dirPath
	 *            Path to create a directory under
	 * 
	 * @return true if creation succeeds, false otherwise
	 * 
	 * @throws IOException
	 *             Path doesn't exist or misc. I/O error
	 */
	public boolean mkdir(String dirPath) throws IOException {
		File file = new File(rootPath.getAbsoluteFile(), dirPath);
		File parentFile = file.getParentFile();
		if (!parentFile.exists()) {
			throw new FileNotFoundException("Path '"
					+ parentFile.getAbsolutePath() + "' doesn't exist!");
		}
		boolean result = file.mkdir();
		return result;
	}

	/**
	 * Create a directory with the given path
	 * 
	 * Intermediate directories are also created by this method
	 * 
	 * @param dirPath
	 *            Path to create a directory under
	 * 
	 * @return true if creation succeeds, false otherwise
	 * 
	 * @throws IOException
	 *             Path doesn't exist or misc. I/O error
	 */
	public boolean mkdirs(String dirPath) {
		File toEncFile = new File(rootPath.getAbsoluteFile(), dirPath);
		boolean result = toEncFile.mkdirs();
		return result;
	}

	/**
	 * Create a file with the given path
	 * 
	 * @param dstFilePath
	 *            Path for the file to create
	 * 
	 * @return EncFSFileInfo for the created file
	 * 
	 * @throws IOException
	 *             File already exists or misc. I/O error
	 */
	public EncFSFileInfo createFile(String dstFilePath) throws IOException {
		if (exists(dstFilePath)) {
			throw new IOException("File already exists");
		}

		File targetFile = getFile(dstFilePath);
		if (targetFile.createNewFile() == false) {
			throw new IOException("failed to create new file");
		}

		return convertToFileInfo(targetFile);
	}

	/**
	 * Copy the file with the given path to another destination
	 * 
	 * @param srcFilePath
	 *            Path to the file to copy
	 * @param dstFilePath
	 *            Path to the destination file
	 * 
	 * @return true if copy was successful, false otherwise
	 * 
	 * @throws IOException
	 *             Destination file already exists, source file doesn't exist or
	 *             misc. I/O error
	 */
	public boolean copy(String srcFilePath, String dstFilePath)
			throws IOException {

		File sourceFile = new File(rootPath.getAbsoluteFile(), srcFilePath);
		File destFile = new File(rootPath.getAbsoluteFile(), dstFilePath);

		if (!sourceFile.exists()) {
			throw new FileNotFoundException("Source file '" + srcFilePath
					+ "' doesn't exist!");
		}

		if (!destFile.exists()) {
			destFile.createNewFile();
		}

		FileChannel source = null;
		FileChannel destination = null;

		try {
			source = new FileInputStream(sourceFile).getChannel();
			destination = new FileOutputStream(destFile).getChannel();
			destination.transferFrom(source, 0, source.size());
		} finally {
			if (source != null) {
				source.close();
			}
			if (destination != null) {
				destination.close();
			}
		}

		return true;
	}

	/**
	 * Open an InputStream to the given file
	 * 
	 * @param srcFilePath
	 *            Path to the source file
	 * 
	 * @return InputStream to read from the file
	 * 
	 * @throws IOException
	 *             Source file doesn't exist or misc. I/O error
	 */
	public InputStream openInputStream(String srcFilePath)
			throws FileNotFoundException {
		File srcF = new File(rootPath.getAbsoluteFile(), srcFilePath);
		return new FileInputStream(srcF);
	}

	/**
	 * Open an OutputStream to the given file
	 * 
	 * @param dstFilePath
	 *            Path to the destination file
	 * 
	 * @return OutputStream to write to the file
	 * 
	 * @throws IOException
	 *             Misc. I/O error
	 */
	public OutputStream openOutputStream(String dstFilePath) throws IOException {
		return openOutputStream(dstFilePath, 0);
	}

	/**
	 * Open an OutputStream to the given file
	 * 
	 * @param dstFilePath
	 *            Path to the destination file
	 * @param outputLength
	 *            Length in bytes of the stream that will be written to this
	 *            stream. It is ignored by this class.
	 * 
	 * @return OutputStream to write to the file
	 * 
	 * @throws IOException
	 *             Misc. I/O error
	 */
	public OutputStream openOutputStream(String dstFilePath, long outputLength)
			throws IOException {
		File srcF = new File(rootPath.getAbsoluteFile(), dstFilePath);
		if (srcF.exists() == false) {
			try {
				srcF.createNewFile();
			} catch (Exception e) {
				throw new IOException(e);
			}
		}
		return new FileOutputStream(srcF);
	}

	// Convert the given File to an EncFSFileInfo
	private EncFSFileInfo convertToFileInfo(File file) {
		String relativePath;
		if (file.equals(rootPath.getAbsoluteFile())) {
			// we're dealing with the root dir
			relativePath = separator;
		} else if (file.getParentFile().equals(rootPath.getAbsoluteFile())) {
			// File is child of the root path
			relativePath = separator;
		} else {
			relativePath = file.getParentFile().getAbsolutePath()
					.substring(rootPath.getAbsoluteFile().toString().length());
		}

		String name = file.getName();
		EncFSFileInfo result = new EncFSFileInfo(name, relativePath,
				file.isDirectory(), file.lastModified(), file.length(),
				file.canRead(), file.canWrite(), file.canExecute());
		return result;
	}

}
