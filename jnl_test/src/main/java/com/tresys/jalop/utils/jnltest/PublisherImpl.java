/*
 * Source code in 3rd-party is licensed and owned by their respective
 * copyright holders.
 *
 * All other source code is copyright Tresys Technology and licensed as below.
 *
 * Copyright (c) 2012 Tresys Technology LLC, Columbia, Maryland, USA
 *
 * This software was developed by Tresys Technology LLC
 * with U.S. Government sponsorship.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tresys.jalop.utils.jnltest;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

import javax.xml.soap.MimeHeaders;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.tresys.jalop.jnl.DigestPair;
import com.tresys.jalop.jnl.Publisher;
import com.tresys.jalop.jnl.PublisherSession;
import com.tresys.jalop.jnl.RecordType;
import com.tresys.jalop.jnl.SourceRecord;

public class PublisherImpl implements Publisher {

	/** A logger for this class. */
	private static final Logger LOGGER = Logger.getLogger(PublisherImpl.class);

	/** The filename for the system meta-data document. */
	private static final String SYS_META_FILENAME = "sys_metadata.xml";

	/** The filename for the application meta-data document. */
	private static final String APP_META_FILENAME = "app_metadata.xml";

	/** The filename for the payload. */
	private static final String PAYLOAD_FILENAME = "payload";

	/** Filename where status information is written to. */
	private static final String STATUS_FILENAME = "status.js";

	/** The format string for output files. */
	private static final String SID_FORMAT_STRING = "0000000000";

	/** Formatter used to generate the sub-directories for each record. */
	static final DecimalFormat SID_FORMATER =
        new DecimalFormat(SID_FORMAT_STRING);

	/** Key in the status file to indicate if a 'sync' message was sent. */
    private static final String SYNCED = "synced";

    /** Key in the status file for the local digest. */
    private static final String LOCALDGST = "local_digest";

    /** Key in the status file for the peer digest. */
    private static final String PEERDGST = "peer_digest";

    /**
     * Root of the input directories. Each record has its own
     * sub-directory.
     */
    final File inputRoot;

    /** The type of records to transfer. */
    private final RecordType recordType;

	public PublisherImpl(final File inputRoot, final RecordType recordType) {

		this.recordType = recordType;

        final String type;
        switch (recordType) {
        case Audit:
            type = "audit";
            break;
        case Journal:
            type = "journal";
            break;
        case Log:
            type = "log";
            break;
        default:
            throw new IllegalArgumentException("illegal record type");
        }
        this.inputRoot = new File(inputRoot, type);
        if (!(this.inputRoot.exists() && this.inputRoot.isDirectory())) {
            throw new RuntimeException("Subdirs don't exist for "
                                       + type);
        }
	}

	@Override
	public SourceRecord getNextRecord(final PublisherSession sess, final String lastSerialId) {

		final long nextSerialId = Long.valueOf(lastSerialId) + 1;

		final File serialDir =
			new File(this.inputRoot,
					SID_FORMATER.format(Long.valueOf(nextSerialId)));

		if(!serialDir.exists()) {
			if(LOGGER.isInfoEnabled()) {
				LOGGER.info("Directory structure for serialId: " + nextSerialId +
						" does not exist. Returning null.");
			}
			return null;
		}

		return new SourceRecordImpl(Long.toString(nextSerialId), 0);
	}

	@Override
	public SourceRecord onJournalResume(final PublisherSession sess, final String serialId,
			final long offset, final MimeHeaders headers) {

		return new SourceRecordImpl(serialId, offset);
	}

	@Override
	public boolean onSubscribe(final PublisherSession sess, final String serialId,
			final MimeHeaders headers) {
		try{
			final long serial = Long.parseLong(serialId);
			if(serial < 0) {
				if(LOGGER.isEnabledFor(Level.ERROR)) {
					LOGGER.error("serialId must be a postive number");
				}
				return false;
			}
		} catch (final NumberFormatException nfe) {
			if(LOGGER.isEnabledFor(Level.ERROR)) {
				LOGGER.error("serialId sent is not numeric - " + serialId);
			}
			return false;
		}
		return true;
	}

	@Override
	public boolean onRecordComplete(final PublisherSession sess, final String serailId,
			final SourceRecord record) {

		final Map<String, String> complete = new HashMap<String, String>();
		complete.put("recordComplete", "true");
		return dumpStatus(record.getSerialId(), complete);
	}

	@Override
	public boolean sync(final PublisherSession sess, final String serialId,
			final MimeHeaders headers) {

		final Map<String, String> synched = new HashMap<String, String>();
		synched.put(SYNCED, "true");
		return dumpStatus(serialId, synched);
	}

	@Override
	public void notifyDigest(final PublisherSession sess, final String serialId,
			final byte[] digest) {

		final String hexString = (new BigInteger(1, digest)).toString(16);
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Calculated digest for " + serialId
                        + ": " + hexString);
        }

        final Map<String, String> map = new HashMap<String, String>();
        map.put(LOCALDGST, hexString);
        dumpStatus(serialId, map);
	}

	@Override
	public void notifyPeerDigest(final PublisherSession sess,
			final Map<String, DigestPair> digestPairs) {

		for(final String key : digestPairs.keySet()) {
			final DigestPair pair = digestPairs.get(key);
			if (LOGGER.isInfoEnabled()) {
				LOGGER.info("Digest status for " + pair.getSerialId()
						+ ": " + pair.getDigestStatus());
			}
			final String hexString = (new BigInteger(1, pair.getPeerDigest())).toString(16);
			final Map<String, String> map = new HashMap<String, String>();
	        map.put(PEERDGST, hexString);
	        dumpStatus(pair.getSerialId(), map);
		}
	}

	/**
     * Write status information about a record out to disk.
     * @param lri The {@link LocalRecordInfo} object output stats for.
     * @return <code>true</code> If the data was successfully written out.
     *         <code>false</code> otherwise.
     */
	@SuppressWarnings("unchecked")
	final boolean dumpStatus(final String serialId, final Map<String, String> statusMap) {

    	final JSONParser p  = new JSONParser();
    	JSONObject status;
    	File statusFile;
    	BufferedOutputStream w;

    	final File serialDir =
            new File(this.inputRoot,
            		SID_FORMATER.format(Long.valueOf(serialId)));

    	try {
    		statusFile = new File(serialDir, STATUS_FILENAME);
    		if(!statusFile.exists()) {
    			if (LOGGER.isInfoEnabled()) {
    				LOGGER.info("The '" + STATUS_FILENAME + "' file does not exist so creating it.");
    			}
    			statusFile.createNewFile();
    			status = new JSONObject();
    		} else {
    			status = (JSONObject) p.parse(new FileReader(statusFile));
    		}

    		status.putAll(statusMap);
    		w = new BufferedOutputStream(new FileOutputStream(statusFile));
    		w.write(status.toJSONString().getBytes("utf-8"));
    		w.close();
    	} catch (final FileNotFoundException e) {
    		LOGGER.error("Failed to open status file for writing:"
                    + e.getMessage());
    		return false;
        } catch (final ParseException e ) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("'" + STATUS_FILENAME + "' file");
            }
            status = new JSONObject();
        } catch (final UnsupportedEncodingException e) {
        	LOGGER.error("cannot find UTF-8 encoder?");
        	return false;
        } catch (final IOException e) {
        	LOGGER.error("failed to write to the status file, aborting");
            return false;
		}

        return true;
    }

	private class SourceRecordImpl implements SourceRecord {

		private final String serialId;
		private final long offset;
		final File serialDir;
		final File sysFile;
		final File appFile;
		final File payloadFile;

		@SuppressWarnings("unchecked")
		public SourceRecordImpl(final String serialId, final long offset) {

			this.serialId = serialId;
			this.offset = offset;

			this.serialDir =
                new File(PublisherImpl.this.inputRoot,
                		PublisherImpl.SID_FORMATER.format(Long.valueOf(serialId)));

			if (LOGGER.isInfoEnabled()) {
				LOGGER.info("Getting files for sysMetadata, appMetadata, and payload.");
			}
			this.sysFile = new File(this.serialDir, SYS_META_FILENAME);
			if(!this.sysFile.exists()) {
				throw new RuntimeException("SysMetadata file doesn't exist");
			}

			this.appFile = new File(this.serialDir, APP_META_FILENAME);

			if(offset > 0 && PublisherImpl.this.recordType == RecordType.Journal
					&& !this.payloadFile.exists()) {
				throw new RuntimeException("Payload file doesn't exist");
			}
			this.payloadFile = new File(this.serialDir, PAYLOAD_FILENAME);

		}

		@Override
		public String getSerialId() {
			return this.serialId;
		}

		@Override
		public RecordType getRecordType() {
			return recordType;
		}

		@Override
		public long getSysMetaLength() {
			if(this.sysFile == null) {
				return 0;
			}
			return this.sysFile.length();
		}

		@Override
		public long getAppMetaLength() {
			if(this.appFile == null) {
				return 0;
			}
			return this.appFile.length();
		}

		@Override
		public long getPayloadLength() {
			if(this.payloadFile == null) {
				return 0;
			}
			return this.payloadFile.length();
		}

		@Override
		public InputStream getSysMetadata() {

			try {
				return new FileInputStream(this.sysFile);
			} catch (final FileNotFoundException e) {
				if(LOGGER.isEnabledFor(Level.ERROR)) {
					LOGGER.error("No SysMetadata file has been set.");
				}
				//Throw an error since sysmetadata file is required
				throw new RuntimeException("SysMetadata file does not exist.");
			}
		}

		@Override
		public InputStream getAppMetadata() {
			try {
				return new FileInputStream(this.appFile);
			} catch (final FileNotFoundException e) {
				if(LOGGER.isInfoEnabled()) {
					LOGGER.info("No AppMetadata file has been set.");
				}
				return null;
			}
		}

		@Override
		public InputStream getPayload() {
			try {
				final InputStream is = new FileInputStream(this.payloadFile);
				return is;
			} catch (final FileNotFoundException e) {
				if(LOGGER.isInfoEnabled()) {
					LOGGER.info("No payload file has been set.");
				}
				return null;
			}
		}
	}

}