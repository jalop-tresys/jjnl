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
package com.tresys.jalop.jnl.exceptions;

/**
 * This exception is thrown when received BEEP message does not contain an
 * expected MIME header.
 */
public class MissingMimeHeaderException extends Exception {
	private static final long serialVersionUID = 1L;
	private final String missingHeader;

	/**
	 * Create a new exception for when a BEEP message is missing an expected
	 * MIME header.
	 * 
	 * @param missingHeader
	 *            The name of the expected header
	 */
	public MissingMimeHeaderException(final String missingHeader) {
		super("Message was missing the expected MIME header '" + missingHeader
				+ "'");
		this.missingHeader = missingHeader;
	}

	/**
	 * Get the the name of the expected header.
	 * 
	 * @return The name of the expected header.
	 */
	String getMissingHeader() {
		return this.missingHeader;
	}
}
