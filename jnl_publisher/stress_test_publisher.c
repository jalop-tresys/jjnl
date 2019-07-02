/***************************************************************************
 *                                  _   _ ____  _
 *  Project                     ___| | | |  _ \| |
 *                             / __| | | | |_) | |
 *                            | (__| |_| |  _ <| |___
 *                             \___|\___/|_| \_\_____|
 *
 * Copyright (C) 1998 - 2018, Daniel Stenberg, <daniel@haxx.se>, et al.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution. The terms
 * are also available at https://curl.haxx.se/docs/copyright.html.
 *
 * You may opt to use, copy, modify, merge, publish, distribute and/or sell
 * copies of the Software, and permit persons to whom the Software is
 * furnished to do so, under the terms of the COPYING file.
 *
 * This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY
 * KIND, either express or implied.
 *
 ***************************************************************************/
/* <DESC>
 * Very simple HTTP GET
 * </DESC>
 */
#include <stdio.h>
#include <iostream>
#include <sys/stat.h>
#include <curl/curl.h>
#include <stdlib.h>
#include <string>
#include <vector>
#include <unordered_map>
#include <boost/algorithm/string.hpp>
#include <fstream>
#include <sstream>
#include <algorithm>

std::unordered_map<std::string, std::string> headerMap;

std::string auditSessionId;
std::string journalSessionId;
std::string logSessionId;
CURL *curl;

const std::string AUDIT = "audit";
const std::string JOURNAL = "journal";
const std::string LOG = "log";

std::string getSessionIdByRecordType(std::string recordType)
{
    if (AUDIT == recordType)
    {
        return auditSessionId;
    }
    else if (JOURNAL == recordType)
    {
        return journalSessionId;
    }
    else if (LOG == recordType)
    {
        return logSessionId;
    }
}

void setSessionIdByRecordType(std::string sessionId, std::string recordType)
{
    if (AUDIT == recordType)
    {
        auditSessionId = sessionId;
    }
    else if (JOURNAL == recordType)
    {
        journalSessionId = sessionId;
    }
    else if (LOG == recordType)
    {
        logSessionId = sessionId;
    }
}

static size_t write_data(void *ptr, size_t size, size_t nmemb, void *stream)
{
    size_t written = fwrite(ptr, size, nmemb, (FILE *)stream);
    return written;
}

static size_t header_callback(char *buffer, size_t size,
                              size_t nitems, void *userdata)
{
    size_t numbytes = size * nitems;
    printf("%.*s\n", numbytes, buffer);

    //Handle headers, split on comma
    std::vector<std::string> result;
    std::string input = std::string(buffer);
    boost::split(result, input, boost::is_any_of(":"));

    //Must only be two for header key/value pair
    if (result.size() == 2)
    {
        std::string headerKey = result[0];
        std::string headerValue = result[1];

        boost::algorithm::trim(headerKey);
        boost::algorithm::trim(headerValue);
        //printf("header key: %s\n", headerKey.c_str());
        //printf("header value: %s\n", headerValue.c_str());

        headerMap[headerKey] = headerValue;
    }

    return numbytes;
}

struct curl_slist * getInitializeHeaders(std::string recordType)
{
    struct curl_slist *headers=NULL;
    headers = curl_slist_append(headers, "Content-Type: application/http+jalop");
    headers = curl_slist_append(headers, "Transfer-Encoding: binary");

    //Set Jalop initialize message
    headers = curl_slist_append(headers, "JAL-Message: initialize");

    //Valid initialize headers
    headers = curl_slist_append(headers, "JAL-Mode: archival");
    headers = curl_slist_append(headers, "JAL-Publisher-Id: ae8a54d7-dd7c-4c50-a7e7-f948a140c556");
    headers = curl_slist_append(headers, "JAL-Accept-Digest: junk digest,http://www.w3.org/2001/04/xmlenc#sha256");
    headers = curl_slist_append(headers, "JAL-Accept-XML-Compression: junk compression, none");

    std::string recordTypeMsg = "JAL-Record-Type: " + recordType;
    headers = curl_slist_append(headers, recordTypeMsg.c_str());
    headers = curl_slist_append(headers, "JAL-Version: 2.0.0.0");
    headers = curl_slist_append(headers, "JAL-Accept-Configure-Digest-Challenge: on");

    return headers;
}

struct curl_slist * getCloseSessionHeaders(std::string recordType)
{
    struct curl_slist *headers=NULL;

    headers = curl_slist_append(headers, "JAL-Message: close-session");

    std::string sessionHeader = "JAL-Session-Id: " + getSessionIdByRecordType(recordType);
    headers = curl_slist_append(headers, sessionHeader.c_str());

    return headers;
}

struct curl_slist * getJALRecordHeaders(std::string recordType, std::string jalId, std::string sysMetadataLength, std::string appMetadataLength, std::string payloadLength)
{
    struct curl_slist *headers=NULL;

    //Sets session id
    std::string sessionIdStr = "JAL-Session-Id: " + getSessionIdByRecordType(recordType);
    headers = curl_slist_append(headers, sessionIdStr.c_str());

    headers = curl_slist_append(headers, "Content-Type: application/http+jalop");
    headers = curl_slist_append(headers, "Transfer-Encoding: binary");

    //Adds JAL-Audit-Format if audit record
    if (recordType == AUDIT)
    {
        headers = curl_slist_append(headers, "JAL-Audit-Format: xml");
    }

    //JAL record headers
    std::string jalIdStr = "JAL-Id: " + jalId;
    headers = curl_slist_append(headers, jalIdStr.c_str());

    std::string sysMetadataStr = "JAL-System-Metadata-Length: " + sysMetadataLength;
    headers = curl_slist_append(headers, sysMetadataStr.c_str());

    std::string appMetadataStr = "JAL-Application-Metadata-Length: " + appMetadataLength;
    headers = curl_slist_append(headers, appMetadataStr.c_str());

    std::string payloadStr;
    if (AUDIT == recordType)
    {
        payloadStr = "JAL-Audit-Length: ";
    }
    else if (JOURNAL == recordType)
    {
        payloadStr = "JAL-Journal-Length: ";
    }
    else
    {
        payloadStr = "JAL-Log-Length: ";
    }
    payloadStr = payloadStr + payloadLength;
    headers = curl_slist_append(headers, payloadStr.c_str());

    std::string recordTypeMsg = "JAL-Message: " + recordType + "-record";
    headers = curl_slist_append(headers, recordTypeMsg.c_str());

    return headers;
}

struct curl_slist * getDigestResponseHeaders(std::string jalId, std::string recordType)
{
    struct curl_slist *headers=NULL;
    headers = curl_slist_append(headers, "Content-Type: application/http+jalop");
    headers = curl_slist_append(headers, "Transfer-Encoding: binary");

    //Set Jalop initialize message
    headers = curl_slist_append(headers, "JAL-Message: digest-response");

    //Valid initialize headers
    std::string jalIdStr = "JAL-Id: " + jalId;
    headers = curl_slist_append(headers, jalIdStr.c_str());
    headers = curl_slist_append(headers, "JAL-Digest-Status: confirmed");


    //Sets session id
    std::string sessionIdStr = "JAL-Session-Id: " + getSessionIdByRecordType(recordType);
    headers = curl_slist_append(headers, sessionIdStr.c_str());

    return headers;
}

bool performHttpPost(struct curl_slist *headers, bool sendBinaryData, std::string recordType, std::string filename)
{
    CURLcode res;


    if(curl)
    {
        //URL to the servlet processing the post
        std::string postUrl = "http://localhost:8080/" + recordType;
        curl_easy_setopt(curl, CURLOPT_URL, postUrl.c_str());
        curl_easy_setopt(curl, CURLOPT_VERBOSE, 1L);

        //Disable verify server cert with known CAs to use for development with self signed certs
        curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0);

        curl_easy_setopt(curl, CURLOPT_SSLCERT, "./cert.pem");
        curl_easy_setopt(curl, CURLOPT_SSLKEY, "./key.pem");

        FILE *fd = NULL;

        //post binary data - just for testing right now, sending file
        if (sendBinaryData)
        {
            struct stat file_info;

            //Source file to post to the jetty http servlet
            char *inputFilename = (char*)filename.c_str();
            fd = fopen(inputFilename, "rb");
            if (!fd)
            {
                fprintf(stderr, "Could not open file.\n");
                if (fd != NULL)
                {
                    fclose(fd);
                }
                return 1;
            }

            if (fstat(fileno(fd), &file_info) != 0)
            {
                fprintf(stderr, "Could not get file information.\n");
                if (fd != NULL)
                {
                    fclose(fd);
                }
                return 1;
            }
            // curl_easy_setopt(easyhandle, CURLOPT_POSTFIELDS, binaryptr);
            curl_easy_setopt(curl, CURLOPT_READDATA, fd);
            curl_easy_setopt(curl, CURLOPT_POST, 1L);

            //set the size of the postfields data
            curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, file_info.st_size);
        }
        else
        {
            //No binary data in body of post, just sending header message.
            curl_easy_setopt(curl, CURLOPT_POST, 1L);

            //set the size of the postfields data
            curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, 0);
        }

        // pass our list of custom made headers
        curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);

        /* send all data to this function  */
        curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, write_data);

        //Function to read response headers
        curl_easy_setopt(curl, CURLOPT_HEADERFUNCTION, header_callback);

        //Perform the post
        res = curl_easy_perform(curl); /* post away! */

        /* Check for errors */
        bool success = false;
        if(res != CURLE_OK)
        {
            success = false;
            fprintf(stderr, "curl_easy_perform() failed: %s\n",
            curl_easy_strerror(res));
        }
        else
        {
            success = true;
            fprintf(stdout, "Request was sucessful\n");
        }

        /* always cleanup */
        curl_easy_reset(curl);

        if (fd != NULL)
        {
            fclose(fd);
        }
        return success;
    }
}

bool processJALRecordResponse(std::string recordType, std::string jalId)
{
    //Process response header
    if (headerMap.find("JAL-Message") != headerMap.end())
    {
        fprintf(stdout, "%s\n", headerMap["JAL-Message"].c_str());

        return performHttpPost(getDigestResponseHeaders(jalId, recordType), false, recordType, "");
    }
    else
    {
        fprintf(stdout, "Missing JAL-Message header in response.\n");
        return false;
    }

    return true;
}

bool sendJalRecords(std::string recordType)
{
    //Sending jal record after successful initialize
    fprintf(stdout, "Received initialize-ack, sending jal record\n");
    std::string jalId = "2ef4e71c-5971-4349-9169-d1e8a2e9450b_2013-11-22T16:09:46.43660-05:00_20705_3167946496";
    struct curl_slist *rec1headers = getJALRecordHeaders(recordType, jalId, "3083", "1179", "1040");

    if (!performHttpPost(rec1headers, true, recordType, "good_audit_input.txt"))
    {
        fprintf(stdout, "JAL-record post failed.\n");
        return false;
    }
    processJALRecordResponse(recordType, jalId);
}

bool processInitializeResponse(std::string recordType)
{
    //Process response header
    if (headerMap.find("JAL-Message") != headerMap.end())
    {
        fprintf(stdout, "%s\n", headerMap["JAL-Message"].c_str());
        if (headerMap["JAL-Message"] == "initialize-ack")
        {
            //Sending jal record after successful initialize
            fprintf(stdout, "Received initialize-ack, sending jal record\n");
            return sendJalRecords(recordType);
        }
        else
        {
            fprintf(stdout, "initialize-nack received. Jalop connection rejected.\n");
            return false;
        }
    }
    else
    {
        fprintf(stdout, "Missing JAL-Message header in response.\n");
        return false;
    }

    return true;
}

int main(void)
{
    curl = curl_easy_init();
     std::string currRecordType = "journal";

    //Send initialize message to audit channel
    if (!performHttpPost(getInitializeHeaders(currRecordType), false, currRecordType, ""))
    {
        fprintf(stdout, "Initialize HTTP post failed.\n");
        exit(1);
    }

    std::string sessionId = headerMap["JAL-Session-Id"];
    setSessionIdByRecordType(sessionId, currRecordType);

    //Send initialize message to journal channel
    currRecordType = "audit";
    if (!performHttpPost(getInitializeHeaders(currRecordType), false, currRecordType, ""))
    {
        fprintf(stdout, "Initialize HTTP post failed.\n");
        exit(1);
    }

    sessionId = headerMap["JAL-Session-Id"];
    setSessionIdByRecordType(sessionId, currRecordType);

    //Send initialize message to log channel
    currRecordType = "log";
    if (!performHttpPost(getInitializeHeaders(currRecordType), false, currRecordType, ""))
    {
        fprintf(stdout, "Initialize HTTP post failed.\n");
        exit(1);
    }
    sessionId = headerMap["JAL-Session-Id"];
    setSessionIdByRecordType(sessionId, currRecordType);

    while(1)
    {
        //If successful post, then proccess response
        sendJalRecords(JOURNAL);

        //If successful post, then proccess response
        sendJalRecords(AUDIT);

        //If successful post, then proccess response
        sendJalRecords(LOG);
    }

    return 0;
}