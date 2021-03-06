/*
 * EncFS Java Library
 * Copyright (C) 2011 Mark R. Pariente
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

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

/**
 * InputStream extension that allows decrypted data to be read from a file on an
 * EncFS volume.
 */
public class EncFSInputStream extends InputStream {

	// Volume that underlying file belongs to
	private final EncFSVolume volume;

	// Volume configuration for this file
	private final EncFSConfig config;

	// Cached block size for this volume
	private final int blockSize;

	// Number of MAC bytes for each block
	private final int numMACBytes;

	// Number of random bytes for each block header
	private final int numRandBytes;

	// Size of the block header for each block
	private final int blockHeaderSize;

	// Current block number for generating block IV
	private int blockNum;

	// Buffer containing decrypted data from the current block
	private byte[] blockBuf;

	// Cursor into blockBuf denoting current stream position
	private int bufCursor;

	// File IV computed from the first 8 bytes of the file
	private byte[] fileIv;

	// Input stream to read data from
	private final InputStream in;

	/**
	 * Create a new EncFSInputStream for reading decrypted data off a file on an
	 * EncFS volume
	 * 
	 * @param volume
	 *            Volume hosting the file to read
	 * @param in
	 *            Input stream to access the raw (encrypted) file contents
	 * 
	 * @throws EncFSCorruptDataException
	 *             File data is corrupt
	 * @throws EncFSUnsupportedException
	 *             Unsupported EncFS configuration
	 * @throws IOException
	 *             File provider returned I/O error
	 */
	public EncFSInputStream(EncFSVolume volume, InputStream in)
			throws EncFSCorruptDataException, EncFSUnsupportedException {
		super();
		this.in = in;
		this.volume = volume;
		this.config = volume.getConfig();
		this.blockSize = config.getBlockSize();
		this.numMACBytes = config.getBlockMACBytes();
		this.numRandBytes = config.getBlockMACRandBytes();
		this.blockHeaderSize = this.numMACBytes + this.numRandBytes;
		this.blockBuf = null;
		this.bufCursor = 0;
		this.blockNum = 0;

		if (config.isUniqueIV()) {
			// Compute file IV
			byte[] fileHeader = new byte[EncFSFile.HEADER_SIZE];
			try {
				in.read(fileHeader);
			} catch (IOException e) {
				throw new EncFSCorruptDataException("Could't read file IV");
			}
			byte[] zeroIv = new byte[8];
			// TODO: external IV chaining changes zeroIv
			try {
				this.fileIv = EncFSCrypto.streamDecode(volume, zeroIv,
						fileHeader);
			} catch (InvalidAlgorithmParameterException e) {
				e.printStackTrace();
			} catch (IllegalBlockSizeException e) {
				throw new EncFSCorruptDataException(e);
			} catch (BadPaddingException e) {
				throw new EncFSCorruptDataException(e);
			}
		} else {
			// No unique IV per file, just use 0
			this.fileIv = new byte[EncFSFile.HEADER_SIZE];
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.io.FileInputStream#read()
	 */
	@Override
	public int read() throws IOException {
		byte[] oneByte = new byte[1];
		int ret = this.read(oneByte, 0, 1);
		if (ret == 1) {
			return oneByte[0];
		}
		return ret;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.io.FileInputStream#read(byte[])
	 */
	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.io.InputStream#read(byte[], int, int)
	 */
	@Override
	public int read(byte[] output, int offset, int size) throws IOException {
		byte[] b = output;
		int len = size;
		int bytesRead = 0;
		int destOffset = offset;
		int bytesToCopy;
		int ret;

		while (bytesRead < len) {

			// Read more data if the data buffer is out
			if ((blockBuf == null) || (bufCursor == (blockBuf.length))) {
				try {
					ret = readBlock();
				} catch (EncFSCorruptDataException e) {
					throw new IOException(e);
				} catch (EncFSUnsupportedException e) {
					throw new IOException(e);
				}

				if (ret < 0) {
					if (bytesRead == 0) {
						return -1;
					} else {
						return bytesRead;
					}
				}
			}

			bytesToCopy = Math
					.min(blockBuf.length - bufCursor, len - bytesRead);
			System.arraycopy(blockBuf, bufCursor, b, destOffset, bytesToCopy);

			bufCursor += bytesToCopy;
			bytesRead += bytesToCopy;
			destOffset += bytesToCopy;
		}

		return bytesRead;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.io.FileInputStream#skip(long)
	 */
	@Override
	public long skip(long n) throws IOException {
		long bytesSkipped = 0;
		int toSkip;
		int bytesRead;

		byte[] skipBuf = new byte[config.getBlockSize()];

		if (n < 0) {
			throw new IOException("Negative skip count");
		}

		while (bytesSkipped < n) {
			toSkip = (int) Math.min(n - bytesSkipped, config.getBlockSize());
			bytesRead = this.read(skipBuf, 0, toSkip);
			bytesSkipped += bytesRead;
			if (bytesRead == -1) {
				return -1; // Already at EOF
			} else if (bytesRead < toSkip) {
				return bytesSkipped; // Hit EOF now
			}
		}

		return bytesSkipped;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.io.InputStream#markSupported()
	 */
	@Override
	public boolean markSupported() {
		// TODO: could support mark()/reset()
		return false;
	}

	@Override
	public void close() throws IOException {
		in.close();
		super.close();
	}

	// Return the block IV for the current block
	private byte[] getBlockIV() {
		long fileIvLong = EncFSUtil.byteArrayToLong(fileIv);
		return EncFSUtil.longToByteArray(blockNum ^ fileIvLong);
	}

	/*
	 * Read one block (blockSize bytes) of data from the underlying
	 * FileInputStream, decrypt it and store it in blockBuf for consumption via
	 * read() methods
	 */
	private int readBlock() throws IOException, EncFSCorruptDataException,
			EncFSUnsupportedException {
		byte[] cipherBuf = new byte[blockSize];
		boolean zeroBlock = false;

		int bytesRead = in.read(cipherBuf, 0, blockSize);
		if (bytesRead == blockSize) { // block decode
			/*
			 * If file holes are allowed then we need to test whether the whole
			 * block is made up of 0's. If not (which is going to be the case
			 * for MAC header by default), we will do block decryption.
			 */
			if (config.isHolesAllowed()) {
				zeroBlock = true;
				for (int i = 0; i < cipherBuf.length; i++)
					if (cipherBuf[i] != 0) {
						zeroBlock = false;
						break;
					}
			}

			try {
				if (zeroBlock == true) {
					blockBuf = cipherBuf;
				} else {
					blockBuf = EncFSCrypto.blockDecode(volume, getBlockIV(),
							cipherBuf);
				}
			} catch (InvalidAlgorithmParameterException e) {
				e.printStackTrace();
			} catch (IllegalBlockSizeException e) {
				throw new EncFSCorruptDataException(e);
			} catch (BadPaddingException e) {
				throw new EncFSCorruptDataException(e);
			}

			bufCursor = blockHeaderSize;
			blockNum++;
		} else if (bytesRead > 0) { // stream decode
			try {
				blockBuf = EncFSCrypto.streamDecode(volume, getBlockIV(),
						cipherBuf, 0, bytesRead);
			} catch (InvalidAlgorithmParameterException e) {
				e.printStackTrace();
			} catch (IllegalBlockSizeException e) {
				throw new EncFSCorruptDataException(e);
			} catch (BadPaddingException e) {
				throw new EncFSCorruptDataException(e);
			}
			bufCursor = blockHeaderSize;
			blockNum++;
		}

		// Verify the block header
		if ((bytesRead > 0) && (blockHeaderSize > 0) && (zeroBlock == false)) {
			byte mac[] = EncFSCrypto.mac64(volume.getMac(), blockBuf,
					numMACBytes);
			for (int i = 0; i < numMACBytes; i++) {
				if (mac[7 - i] != blockBuf[i]) {
					throw new EncFSCorruptDataException("Block MAC mismatch");
				}
			}
		}

		return bytesRead;
	}
}
